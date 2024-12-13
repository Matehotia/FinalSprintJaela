package mg.itu.prom16;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

import mg.itu.prom16.Annotations.*;

@WebServlet(name = "FrontController", urlPatterns = { "/" })
public class FrontController extends HttpServlet {
    private HashMap<String, Mapping> urlMappings = new HashMap<>();
    protected ArrayList<String> listeControlleurs = new ArrayList<>();

    public void getListeControlleurs(String packagename) throws Exception {
        if (packagename == null || packagename.isEmpty()) {
            throw new Exception("Package name is empty or null");
        }

        String bin_path = "WEB-INF/classes/" + packagename.replace(".", "/");
        bin_path = getServletContext().getRealPath(bin_path);

        File b = new File(bin_path);
        if (!b.exists()) {
            throw new Exception("Package directory does not exist: " + bin_path);
        }

        boolean hasController = false;

        for (File fichier : b.listFiles()) {
            if (fichier.isFile() && fichier.getName().endsWith(".class")) {
                String className = packagename + "." + fichier.getName().replace(".class", "");
                Class<?> classe = Class.forName(className);
                if (classe.isAnnotationPresent(Controller.class)) {
                    hasController = true;

                    for (Method method : classe.getDeclaredMethods()) {
                        String url = null;
                        String verb = "GET";

                        if (method.isAnnotationPresent(Url.class)) {
                            Url urlAnnotation = method.getAnnotation(Url.class);
                            url = urlAnnotation.value();
                        }
                        if (method.isAnnotationPresent(Post.class)) {
                            verb = "POST";
                        }

                        if (url != null) {
                            Mapping mapping = urlMappings.get(url);
                            if (mapping == null) {
                                mapping = new Mapping(classe.getName());
                                urlMappings.put(url, mapping);
                            }

                            Class<?>[] paramTypes = method.getParameterTypes();
                            VerbAction verbAction = new VerbAction(method.getName(), verb, paramTypes);
                            mapping.addVerbAction(verbAction);
                            urlMappings.put(url, mapping);
                        }
                    }
                }
            }
        }

        if (!hasController) {
            throw new Exception("No controllers found in package: " + packagename);
        }
    }

    @Override
    public void init() throws ServletException {
        super.init();
    }

    protected void processRequest(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String contextPath = req.getContextPath();
        String urlPath = req.getRequestURI().substring(contextPath.length());
        PrintWriter out = resp.getWriter();

        try {
            if (urlMappings.isEmpty()) {
                try {
                    getListeControlleurs(getServletContext().getInitParameter("controllerPackage"));
                } catch (Exception e) {
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    displayError(resp, e);
                    urlMappings.clear();
                    return;
                }
            }

            if (urlPath == null || urlPath.isEmpty() || urlPath.equals("/")) {
                req.getRequestDispatcher("index.jsp").forward(req, resp);
                return;
            }

            Mapping mapping = urlMappings.get(urlPath);
            if (mapping == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.println("<html><body>");
                out.println("<h1>400 Bad Request</h1>");
                out.println("<p>The URL you requested does not exist on this server.</p>");
                out.println("</body></html>");
                return;
            }

            String requestMethod = req.getMethod();
            VerbAction verbAction = mapping.getVerbAction(requestMethod);
            if (verbAction == null) {
                resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                out.println("<html><body>");
                out.println("<h1>405 Method Not Allowed</h1>");
                out.println("<p>Verb " + requestMethod + " is not allowed for the URL " + urlPath + ".</p>");
                out.println("<p>Use " + mapping.getAvailableVerbs() + " instead.</p>");
                out.println("</body></html>");
                return;
            }

            Class<?> clazz = Class.forName(mapping.getClassName());
            Method method = getMethodByName(clazz, verbAction.getAction(), verbAction.getParameterTypes());
            Object result = invokeMethod(req, mapping.getClassName(), method);

            if (method.isAnnotationPresent(Restapi.class)) {
                resp.setContentType("application/json;charset=UTF-8");
                Gson gson = new Gson();
                String jsonResponse = gson.toJson(result);
                out.print(jsonResponse);
            } else {
                if (result instanceof String) {
                    out.println("<!DOCTYPE html>");
                    out.println("<html>");
                    out.println("<head>");
                    out.println("<title>Mapping Information</title>");
                    out.println("</head>");
                    out.println("<body>");
                    out.println("<h1>Information for URL: " + urlPath + "</h1>");
                    out.println("</body>");
                    out.println("</html>");
                } else if (result instanceof ModelView) {
                    ModelView modelView = (ModelView) result;
                    String viewUrl = modelView.getUrl();
                    HashMap<String, Object> data = modelView.getData();
                    for (String key : data.keySet()) {
                        req.setAttribute(key, data.get(key));
                    }
                    req.getRequestDispatcher(viewUrl).forward(req, resp);
                } else {
                    throw new Exception("Return type not recognized for URL path: " + urlPath);
                }
            }
        } catch (Exception e) {
            displayError(resp, e);
        }
    }

    private Object invokeMethod(HttpServletRequest req, String className, Method method)
            throws IOException {
        Object result = null;
        try {
            Class<?> clazz = Class.forName(className);
            method.setAccessible(true);

            Object[] args = new Object[method.getParameterCount()];
            Class<?>[] parameterTypes = method.getParameterTypes();
            Parameter[] parameters = method.getParameters();

            for (int i = 0; i < parameters.length; i++) {
                Parameter param = parameters[i];
                Class<?> paramType = parameterTypes[i];
                String paramName = param.getName();

                if (paramType == MySession.class) {
                    HttpSession session = req.getSession();
                    args[i] = new MySession(session);
                    continue;
                }

                if (param.isAnnotationPresent(RequestObject.class)) {
                    String prefix = param.getAnnotation(RequestObject.class).value();
                    args[i] = populateObjectFromRequest(paramType, req, prefix);
                    continue;
                }

                String paramValue = req.getParameter(paramName);
                args[i] = convertParameterValue(paramValue, paramType);
            }

            Object instance = clazz.getDeclaredConstructor().newInstance();
            result = method.invoke(instance, args);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException(e);
        }

        return result;
    }

    private void displayError(HttpServletResponse resp, Exception e) throws IOException {
        PrintWriter out = resp.getWriter();
        resp.setContentType("text/html;charset=UTF-8");
        out.println("<!DOCTYPE html>");
        out.println("<html>");
        out.println("<head>");
        out.println("<title>Error</title>");
        out.println("</head>");
        out.println("<body>");
        out.println("<h1>Error: " + e.getMessage() + "</h1>");
        for (StackTraceElement ste : e.getStackTrace()) {
            out.println("<p>" + ste + "</p>");
        }
        out.println("</body>");
        out.println("</html>");
    }

    public Method getMethodByName(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        try {
            return clazz.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private Object convertParameterValue(String paramValue, Class<?> parameterType) {
        if (parameterType == String.class) {
            return paramValue;
        } else if (parameterType == int.class || parameterType == Integer.class) {
            return Integer.parseInt(paramValue);
        } else if (parameterType == double.class || parameterType == Double.class) {
            return Double.parseDouble(paramValue);
        } else if (parameterType == boolean.class || parameterType == Boolean.class) {
            return Boolean.parseBoolean(paramValue);
        }
        return paramValue;
    }

    private Object populateObjectFromRequest(Class<?> objectType, HttpServletRequest req, String prefix)
            throws Exception {
        Object obj = objectType.getDeclaredConstructor().newInstance();
        Field[] fields = objectType.getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            String paramName = (prefix.isEmpty() ? "" : prefix + ".") + field.getName();
            String paramValue = req.getParameter(paramName);
            if (paramValue != null) {
                field.set(obj, convertParameterValue(paramValue, field.getType()));
            }
        }
        return obj;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }
}
