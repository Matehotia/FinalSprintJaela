package mg.itu.prom16;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.lang.reflect.Method;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mg.itu.prom16.Annotations.*;
import mg.itu.prom16.*;

public class FrontController extends HttpServlet {
    private HashMap<String, Mapping> urlMappings = new HashMap<>();
    protected ArrayList<String> listeControlleurs = new ArrayList<String>();

    public void getListeControlleurs(String packagename) throws Exception {
        String bin_path = "WEB-INF/classes/" + packagename.replace(".", "/");
        bin_path = getServletContext().getRealPath(bin_path);

        File b = new File(bin_path);
        for (File fichier : b.listFiles()) {
            if (fichier.isFile() && fichier.getName().endsWith(".class")) {
                String className = packagename + "." + fichier.getName().replace(".class", "");
                Class<?> classe = Class.forName(className);
                if (classe.isAnnotationPresent(Controller.class)) {
                    for (Method method : classe.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(Get.class)) {
                            Get getAnnotation = method.getAnnotation(Get.class);
                            String url = getAnnotation.value();
                            Mapping mapping = new Mapping(classe.getName(), method.getName());
                            urlMappings.put(url, mapping);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            getListeControlleurs(getServletContext().getInitParameter("controllerPackage"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void processRequest(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, IOException {
        String contextPath = req.getContextPath(); // Obtenir le contexte de l'application
        String urlPath = req.getRequestURI().substring(contextPath.length()); // Enlever le contexte de l'application        
        if (urlPath == null || urlPath.isEmpty()) {
            resp.getWriter().println("No URL path provided");
            return;
        }

        Mapping mapping = urlMappings.get(urlPath);
        PrintWriter out = resp.getWriter();
        try {
               // Afficher le contenu du HashMap urlMappings
               out.println("Contenu de urlMappings:");
               for (HashMap.Entry<String, Mapping> entry : urlMappings.entrySet()) {
                   String url = entry.getKey();
                   Mapping mapping2 = entry.getValue();
                   out.println("URL: " + url + " - Class: " + mapping2.getClassName() + ", Method: " + mapping2.getMethodName());
               }

                getListeControlleurs(getServletContext().getInitParameter("controllerPackage"));
                resp.setContentType("text/html;charset=UTF-8");
                if (mapping != null) {
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
                    out.println("</ul>");
                    out.println("</body>");
                    out.println("</html>");
                } else {
                    out.println("<!DOCTYPE html>");
                    out.println("<html>");
                    out.println("<head>");
                    out.println("<title>No Mapping Found</title>");
                    out.println("</head>");
                    out.println("<body>");
                    out.println("<h1>No method associated with this URL path: " + urlPath + "</h1>");
                    out.println("</body>");
                    out.println("</html>");
                }            
        } catch (Exception e) {
            out.println(e.getMessage());
            for (StackTraceElement ste : e.getStackTrace()) {
                out.println(ste);
            }
        }
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
