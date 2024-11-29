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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Enumeration;
import mg.itu.prom16.Annotations.*;

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

        // Verify in the package if controllers exist
        boolean hasController = false;

        for (File fichier : b.listFiles()) {
            if (fichier.isFile() && fichier.getName().endsWith(".class")) {
                String className = packagename + "." + fichier.getName().replace(".class", "");
                Class<?> classe = Class.forName(className);
                if (classe.isAnnotationPresent(Controller.class)) {
                    hasController = true;
                    for (Method method : classe.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(Get.class)) {
                            Get getAnnotation = method.getAnnotation(Get.class);
                            String url = getAnnotation.value();
                            if (urlMappings.containsKey(url)) {
                                throw new Exception("Duplicate URL mapping found for URL: " + url);
                            }
                            Mapping mapping = new Mapping(classe.getName(), method.getName());
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
        // Defer the package initialization to the first request to display custom
        // errors
    }

    protected void processRequest(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String contextPath = req.getContextPath();
        String urlPath = req.getRequestURI().substring(contextPath.length());

        PrintWriter out = resp.getWriter();
        try {
            // Initialize controllers if not already initialized
            if (urlMappings.isEmpty()) {
                try {
                    getListeControlleurs(getServletContext().getInitParameter("controllerPackage"));
                } catch (Exception e) {
                    displayError(resp, e);
                    return;
                }
            }

            // Check if no URL path is provided and just return without an error
            if (urlPath == null || urlPath.isEmpty() || urlPath.equals("/")) {
                resp.setContentType("text/html;charset=UTF-8");
                out.println("<!DOCTYPE html>");
                out.println("<html>");
                out.println("<head>");
                out.println("<title>Welcome</title>");
                out.println("</head>");
                out.println("<body>");
                out.println("<h1>Welcome to the Front Controller</h1>");
                out.println("</body>");
                out.println("</html>");
                return;
            }

            Mapping mapping = urlMappings.get(urlPath);
            if (mapping == null) {
                throw new Exception("No mapping found for URL path: " + urlPath);
            }

            resp.setContentType("text/html;charset=UTF-8");
            out.println("Class Name: " + mapping.getClassName() + "</li>");
            out.println("Method Name: " + mapping.getMethodName() + "</li>");
            Class<?> clazz = Class.forName(mapping.getClassName());
            Method method = getMethodByName(clazz, mapping.getMethodName());
            Object result = invokeMethod(req, mapping.getClassName(), method);

            if (result instanceof String) {
                out.println("<!DOCTYPE html>");
                out.println("<html>");
                out.println("<head>");
                out.println("<title>Mapping Information</title>");
                out.println("</head>");
                out.println("<body>");
                out.println("<h1>Information for URL: " + urlPath + "</h1>");
                out.println("<ul>");
                out.println("<li>Class Name: " + mapping.getClassName() + "</li>");
                out.println("<li>Method Name: " + mapping.getMethodName() + "</li>");
                out.println("<li>Message du méthode: " + result + "</li>");
                out.println("</ul>");
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
        } catch (Exception e) {
            displayError(resp, e);
        }
    }

    private Object invokeMethod(HttpServletRequest req, String className, Method method)
            throws IOException, NoSuchMethodException {
        Object result = null;
        try {
            Class<?> clazz = Class.forName(className);
            method.setAccessible(true);

            Object[] args = new Object[method.getParameterCount()];
            Class<?>[] parameterTypes = method.getParameterTypes();
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            Parameter[] parameters = method.getParameters();

            Map<Integer, String> paramIndexToNameMap = new HashMap<>();
            for (int i = 0; i < parameters.length; i++) {
                paramIndexToNameMap.put(i, parameters[i].getName());
            }

            Enumeration<String> parameterNames = req.getParameterNames();
            while (parameterNames.hasMoreElements()) {
                String paramName = parameterNames.nextElement();
                String paramValue = req.getParameter(paramName);
                boolean paramResolved = false;

                // Vérifier d'abord par le nom de la variable de méthode
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (paramName.equals(paramIndexToNameMap.get(i))) {
                        args[i] = convertParameterValue(paramValue, parameterTypes[i]);
                        paramResolved = true;
                        continue;
                    }
                }

                // Si le paramètre n'est pas résolu via le nom de variable, vérifier les
                // annotations
                if (!paramResolved) {
                    for (int i = 0; i < parameterAnnotations.length; i++) {
                        Annotation[] annotations = parameterAnnotations[i];
                        for (Annotation annotation : annotations) {
                            if (annotation instanceof Param) {
                                String annotationValue = ((Param) annotation).name();
                                if (paramName.equals(annotationValue)) {
                                    args[i] = convertParameterValue(paramValue, parameterTypes[i]);
                                    paramResolved = true;
                                    break;
                                }
                            }
                        }
                        if (paramResolved) {
                            break;
                        }
                    }
                }
            }

            for (int i = 0; i < parameterTypes.length; i++) {
                if (args[i] == null) {
                    if (parameters[i].isAnnotationPresent(RequestObject.class)) {
                        RequestObject requestObjectAnnotation = parameters[i].getAnnotation(RequestObject.class);
                        String prefix = requestObjectAnnotation.value();
                        args[i] = populateObjectFromRequest(parameterTypes[i], req, prefix);
                    } else if (req.getParameter(parameters[i].getName()) != null) {
                        args[i] = convertParameterValue(req.getParameter(parameters[i].getName()), parameterTypes[i]);
                    }
                }
            }

            // Vérifier s'il y a des arguments non résolus et non annotés
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null &&
                        !parameters[i].isAnnotationPresent(Param.class) &&
                        !parameters[i].isAnnotationPresent(RequestObject.class)) {
                    throw new Exception("ETU2677: Parameter " + parameters[i].getName() +
                            " in method " + method.getName() +
                            " of class " + className +
                            " is not annotated by @Param or @RequestObject");
                }
            }

            Object instance = clazz.getDeclaredConstructor().newInstance();
            result = method.invoke(instance, args);

        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException(e); // Propager l'exception pour être traitée ailleurs
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

    public Method getMethodByName(Class<?> clazz, String methodName) {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method m : methods) {
            if (m.getName().equals(methodName)) {
                try {
                    Class<?>[] parameterTypes = m.getParameterTypes();
                    return clazz.getMethod(methodName, parameterTypes);
                } catch (NoSuchMethodException ex) {
                    ex.printStackTrace();
                }
            }
        }
        return null;
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
        } else {
            // Gérer d'autres types de paramètres selon les besoins
            return paramValue;
        }
    }

    private Object populateObjectFromRequest(Class<?> objectType, HttpServletRequest req, String prefix)
            throws Exception {
        Object obj = objectType.getDeclaredConstructor().newInstance();
        Field[] fields = objectType.getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            String paramName = (prefix.isEmpty() ? "" : prefix + ".") + field.getName();
            if (field.isAnnotationPresent(RequestField.class)) {
                RequestField requestField = field.getAnnotation(RequestField.class);
                paramName = prefix + "." + requestField.value();
            }
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
