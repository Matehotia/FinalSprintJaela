package mg.itu.prom16;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mg.itu.prom16.Annotations.Controller;

public class FrontController extends HttpServlet {
    protected ArrayList<String> listeControlleurs = new ArrayList<String>();
    protected boolean checked;

    public void getListeControlleurs(String packagename) throws Exception {
        String bin_path = "WEB-INF/classes/" + packagename.replace(".", "/");

        bin_path = getServletContext().getRealPath(bin_path);

        File b = new File(bin_path);
        for (File fichier : b.listFiles()) {
            if (fichier.isFile() && fichier.getName().endsWith(".class")) {
                Class<?> classe = Class.forName(packagename + "." + fichier.getName().split(".class")[0]);
                if (classe.isAnnotationPresent(mg.itu.prom16.Annotations.Controller.class))
                    listeControlleurs.add(classe.getName());
            }
        }
    }

    @Override
    public void init() throws ServletException {
        super.init();
        checked = false;
    }

    protected void processRequest(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, IOException {
        PrintWriter out = resp.getWriter();
        try {
            if (!checked) {
                getListeControlleurs(getServletContext().getInitParameter("controllerPackage"));
                checked = true;
            }
            resp.setContentType("text/html;charset=UTF-8");
            out.println("<!DOCTYPE html>");
            out.println("<html>");
            out.println("<head>");
            out.println("<title>SPRINT1</title>");
            out.println("</head>");
            out.println("<body>");
            out.println("<h1>Liste des Controllers dans le package :</h1>");
            out.println("<ul>");
            for (String controlleur : listeControlleurs) {
                out.println("<li>" + controlleur + "</li>");
            }
            out.println("</ul>");
            out.println("</body>");
            out.println("</html>");
            
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
