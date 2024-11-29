package mg.itu.prom16;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mg.itu.prom16.annotation.AnnotationController;
import mg.itu.prom16.annotation.Get;
import mg.itu.prom16.annotation.RequestObject;
import mg.itu.prom16.annotation.RequestField;
import mg.itu.prom16.annotation.RequestParameter;

public class FrontController extends HttpServlet {
    private ArrayList<String> listeControlleurs = new ArrayList<>();
    private HashMap<String, Mapping> urlMappings = new HashMap<>();
    private List<Exception> exceptions = new ArrayList<>();

    public void getListeControllers(String packagename) throws ClassNotFoundException {
        String bin = "WEB-INF/classes/" + packagename.replace(".", "/");
        bin = getServletContext().getRealPath(bin);

        File b = new File(bin);
        if (!b.exists() || !b.isDirectory()) {
            exceptions.add(new Exception("Le package spécifié n'existe pas : " + packagename));
            return;
        }

        for (File onefile : b.listFiles()) {
            if (onefile.isFile() && onefile.getName().endsWith(".class")) {
                Class<?> clazz = Class.forName(packagename + "." + onefile.getName().split(".class")[0]);
                if (clazz.isAnnotationPresent(AnnotationController.class))
                    listeControlleurs.add(clazz.getName());
            }
        }
    }

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            getListeControllers(getServletContext().getInitParameter("controlleurs"));
            for (String controlleur : listeControlleurs) {
                Class<?> clazz = Class.forName(controlleur);
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Get.class)) {
                        Get getAnnotation = method.getAnnotation(Get.class);
                        String url = getAnnotation.value();
                        if (urlMappings.containsKey(url)) {
                            exceptions.add(new ServletException("Duplication d'URL trouvée: " + url +
                                    " est déjà liée à " + urlMappings.get(url)));
                        } else {
                            Mapping mapping = new Mapping(clazz.getName(), method.getName());
                            urlMappings.put(url, mapping);
                        }
                    }
                }
            }
        } catch (Exception e) {
            exceptions.add(e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            processRequest(req, resp);
        } catch (Exception e) {
            handleExceptions(req, resp, e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            processRequest(req, resp);
        } catch (Exception e) {
            handleExceptions(req, resp, e);
        }
    }

    protected void processRequest(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String requestUrl = req.getRequestURI().substring(req.getContextPath().length());
        if (requestUrl.contains("?")) {
            requestUrl = requestUrl.substring(0, requestUrl.indexOf("?"));
        }
        Mapping mapping = urlMappings.get(requestUrl);
    
        if (mapping == null) {
            exceptions.add(new Exception("Aucune méthode n'est associée à cet URL: " + requestUrl));
        } else {
            try {
                Class<?> clazz = Class.forName(mapping.getClassName());
                Method method = getMethodByName(clazz, mapping.getMethodName());
                if (method == null) {
                    exceptions.add(new Exception("Méthode non trouvée: " + mapping.getMethodName()));
                    return;
                }
    
                Object[] args = new Object[method.getParameterCount()];
                Class<?>[] parameterTypes = method.getParameterTypes();
                Parameter[] parameters = method.getParameters();
    
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (parameterTypes[i] == MySession.class) {
                        args[i] = new MySession(req.getSession());
                    } else if (parameters[i].isAnnotationPresent(RequestObject.class)) {
                        RequestObject requestObjectAnnotation = parameters[i].getAnnotation(RequestObject.class);
                        String prefix = requestObjectAnnotation.value();
                        args[i] = populateObjectFromRequest(parameterTypes[i], req, prefix);
                    } else if (parameters[i].isAnnotationPresent(RequestParameter.class)) {
                        RequestParameter requestParameterAnnotation = parameters[i].getAnnotation(RequestParameter.class);
                        String paramName = requestParameterAnnotation.value();
                        args[i] = convertParameterValue(req.getParameter(paramName), parameterTypes[i]);
                    } else {
                        throw new Exception("Le paramètre " + parameters[i].getName() + " de la méthode " + method.getName() + " n'est pas annoté. Annoter le!!!!");
                    }
                }
    
                if (exceptions.isEmpty()) {
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    Object result = method.invoke(instance, args);
    
                    if (result instanceof String) {
                        PrintWriter out = resp.getWriter();
                        out.println("Le résultat obtenu est un string : " + result);
                    } else if (result instanceof ModelView) {
                        ModelView modelView = (ModelView) result;
                        for (HashMap.Entry<String, Object> entry : modelView.getData().entrySet()) {
                            req.setAttribute(entry.getKey(), entry.getValue());
                        }
                        req.getRequestDispatcher(modelView.getUrl()).forward(req, resp);
                    } else {
                        exceptions.add(new Exception("Type de retour non reconnu : " + result.getClass().getName()));
                    }
                }
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
    
        if (!exceptions.isEmpty()) {
            handleExceptions(req, resp, null);
        }
    }
    
    
    
    private Method getMethodByName(Class<?> clazz, String methodName) {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }


    private void handleExceptions(HttpServletRequest req, HttpServletResponse resp, Exception e) throws IOException {
        PrintWriter out = resp.getWriter();
        resp.setContentType("text/html");

        out.println("<html><body>");
        out.println("<h1>Des erreurs se sont produites:</h1>");
        out.println("<ul>");
        for (Exception exception : exceptions) {
            out.println("<li>" + exception.getMessage() + "</li>");
            for (StackTraceElement ste : exception.getStackTrace()) {
                out.println("<li>&emsp;" + ste + "</li>");
            }
        }
        out.println("</ul>");
        out.println("</body></html>");

        exceptions.clear();
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

    private Object populateObjectFromRequest(Class<?> objectType, HttpServletRequest req, String prefix) throws Exception {
        Object obj = objectType.getDeclaredConstructor().newInstance();
        Field[] fields = objectType.getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            String paramName = (prefix.isEmpty() ? "" : prefix + ".") + field.getName();
            if (field.isAnnotationPresent(RequestField.class)) {
                RequestField requestField = field.getAnnotation(RequestField.class);
                paramName = prefix + "."+requestField.value();
            }
            String paramValue = req.getParameter(paramName);
            if (paramValue != null) {
                field.set(obj, convertParameterValue(paramValue, field.getType()));
            }
        }

        return obj;
    }
}
