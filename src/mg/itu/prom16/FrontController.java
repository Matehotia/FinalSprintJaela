package mg.itu.prom16;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.*;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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

        // Verifie dans le package si les controlleurs existent
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

                            try {
                                Class<?>[] paramTypes = method.getParameterTypes();
                                VerbAction verbAction = new VerbAction(method.getName(), verb, paramTypes);
                                
                                // Vérification s'il existe déjà un VerbAction avec le même verbe et action
                                mapping.addVerbAction(verbAction);
                                urlMappings.put(url, mapping);
    
                            } catch (Exception e) {
                                throw e;
                            }
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
            // Initialisation des contrôleurs si nécessaire
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

            // Si aucune URL fournie, afficher un message de bienvenue
            if (urlPath == null || urlPath.isEmpty() || urlPath.equals("/")) {
                req.getRequestDispatcher("index.jsp").forward(req, resp);
                return;
            }

            Mapping mapping = urlMappings.get(urlPath);
            if (mapping == null) {
                // URL not found, respond with 400 error
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.println("<html><body>");
                out.println("<h1>400 Bad Request</h1>");
                out.println("<p>The URL you requested does not exist on this server.</p>");
                out.println("</body></html>");
                return;
            }

            // Vérification du verbe HTTP
            String requestMethod = req.getMethod();
            VerbAction verbAction = mapping.getVerbAction(requestMethod);
            if (verbAction == null) {
                if (verbAction == null) {
                    // Si le verbe HTTP est incorrect, renvoyer une erreur 405
                    resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                    out.println("<html><body>");
                    out.println("<h1>405 Method Not Allowed</h1>");
                    out.println("<p>Verb " + requestMethod + " is not allowed for the URL " + urlPath + ".</p>");
                    out.println("<p>Use " + mapping.getAvailableVerbs() + " instead.</p>");
                    out.println("</body></html>");
                    return;
                }
            }

            Class<?> clazz = Class.forName(mapping.getClassName());
            Method method = getMethodByName(clazz, verbAction.getAction(), verbAction.getParameterTypes());
            Object result = invokeMethod(req, mapping.getClassName(), method);

            // Vérifier si la méthode est annotée avec @Restapi
            if (method.isAnnotationPresent(Restapi.class)) {
                resp.setContentType("application/json;charset=UTF-8");
                // Utilisation de Gson pour convertir en JSON
                Gson gson = new Gson();
                String jsonResponse;
                if (result instanceof ModelView) {
                    // Si c'est un ModelView, renvoyer les données
                    ModelView modelView = (ModelView) result;
                    jsonResponse = gson.toJson(modelView.getData());
                } else {
                    // Sinon, transformer directement le résultat en JSON
                    jsonResponse = gson.toJson(result);
                }
                out.print(jsonResponse);
            } else {
                // Traitement normal avec retour d'une vue (JSP)
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
                    out.println("<li>Method Name: " + verbAction.getAction() + "</li>");
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

                // Si le paramètre n'est pas résolu via le nom de variable, vérifier les annotations
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

            // Gérer les paramètres de type MySession
            for (int i = 0; i < parameterTypes.length; i++) {
                if (args[i] == null) {
                    if (parameters[i].getType().equals(MySession.class)) {
                        HttpSession session = req.getSession();
                        args[i] = new MySession(session);
                    } else if (parameters[i].isAnnotationPresent(RequestObject.class)) {
                        RequestObject requestObjectAnnotation = parameters[i].getAnnotation(RequestObject.class);
                        String prefix = requestObjectAnnotation.value();
                        args[i] = populateObjectFromRequest(parameterTypes[i], req, prefix);
                    } else if (req.getParameter(parameters[i].getName()) != null) {
                        args[i] = convertParameterValue(req.getParameter(parameters[i].getName()), parameterTypes[i]);
                    }
                }

            // Vérifier s'il y a des arguments non résolus et non annotés
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null &&
                        !parameters[i].isAnnotationPresent(Param.class) &&
                        !parameters[i].isAnnotationPresent(RequestObject.class)) {
                    throw new Exception(" Parameter " + parameters[i].getName() +
                            " in method " + method.getName() +
                            " of class " + className +
                            " is not annotated by @Param or @RequestObject");
                }
            }

            Object instance = clazz.getDeclaredConstructor().newInstance();
            result = method.invoke(instance, args);

        } catch (Exception e) {
            e.printStackTrace();
            e.getCause();
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
        out.println("<h1>Error "+ resp.getStatus() +" : " + e.getMessage() + "</h1>");
        for (StackTraceElement ste : e.getStackTrace()) {
            String stackTraceLine = ste.toString();
            if (!stackTraceLine.contains("org.apache.catalina") &&
                    !stackTraceLine.contains("org.apache.coyote") &&
                    !stackTraceLine.contains("org.apache.tomcat") &&
                    !stackTraceLine.contains("java.base/java.lang.Thread")) {
                out.println("<p>" + ste + "</p>");
            }
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
