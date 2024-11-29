package mg.itu.prom16;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class Mapping {
    private String className;
    private Set<VerbAction> verbActions;

    public Mapping(String className) {
        this.className = className;
        this.verbActions = new HashSet<>();
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public Set<VerbAction> getVerbActions() {
        return verbActions;
    }

    public boolean hasVerbAction(String verb, String action) {
        for (VerbAction v : verbActions) {
            if (v.getVerb().equalsIgnoreCase(verb) && v.getAction().equals(action)) {
                return true;
            }
        }
        return false;
    }

    public void addVerbAction(VerbAction verbAction) throws Exception {
        // Si l'ajout échoue, cela signifie que le verbAction est déjà présent
        if (!this.verbActions.add(verbAction)) {
            throw new Exception("Duplicate method and verb combination: " + verbAction.getAction() + " with verb " + verbAction.getVerb());
        }
    }

    public VerbAction getVerbAction(String verb) {
        for (VerbAction verbAction : verbActions) {
            if (verbAction.getVerb().equalsIgnoreCase(verb)) {
                return verbAction;
            }
        }
        return null;
    }

    public Set<String> getAvailableVerbs() {
        return verbActions.stream()
                        .map(VerbAction::getVerb)
                        .collect(Collectors.toSet());
    }
}
