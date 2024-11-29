package mg.itu.prom16;

public class Mapping {
    String className;
    String methodName;

    public Mapping(String className, String methodName) {
        this.setClassName(className);
        this.setMethodName(methodName);
    }

    public String getClassName() {
        return className;
    }
    public void setClassName(String className) {
        this.className = className;
    }
    public String getMethodName() {
        return methodName;
    }
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }
}
