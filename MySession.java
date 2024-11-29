package mg.itu.prom16;

public class MySession {
    private HttpSession session;

    public MySession(HttpSession session) {
        this.session = session;
    }

    public Object get(String key) {
        return session.getAttribute(key);
    }

    public void add(String key, Object value) {
        session.setAttribute(key, value);
    }

    public void delete(String key) {
        session.removeAttribute(key);
    }
}
