package mg.itu.prom16;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
            Class<?> clazz = Class.forName(mapping.getClassName());
            Object instance = clazz.getDeclaredConstructor().newInstance();
            Method method = clazz.getDeclaredMethod(mapping.getMethodName());
            Object result = method.invoke(instance);

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

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }
}
