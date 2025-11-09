package ma.emsi.elbouaamri.tp4appweb.jsf;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Backing bean pour la page JSF index.xhtml.
 * Portée view pour conserver l'état de la conversation (chat).
 */
@Named("bb")
@ViewScoped
public class Bb implements Serializable {

    private String roleSysteme;
    private boolean roleSystemeChangeable = true;

    private String question;
    private String reponse;
    private StringBuilder conversation = new StringBuilder();

    @Inject
    private LlmClient llm; // Client qui parle au LLM via LangChain4j

    // --- Actions UI ---
    /** Bouton "Envoyer" */
    public String envoyer() {
        if (question == null || question.isBlank()) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Texte question vide", "Il manque le texte de la question.");
            return null;
        }

        try {
            // Au tout début, on fixe le rôle système dans la mémoire et on le verrouille
            if (roleSystemeChangeable) {
                llm.setSystemRole(roleSysteme);
                roleSystemeChangeable = false;
            }

            // Appel direct au LLM
            reponse = llm.ask(question);

            // Historique affiché dans la zone "conversation"
            appendConversation(question, reponse);

        } catch (Exception e) {
            reponse = null;
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur LLM", e.getMessage());
        }
        return null; // rester sur la même vue
    }

    /** Bouton "Nouveau chat" : on repart de zéro (nouvelle instance @ViewScoped) */
    public String nouveauChat() {
        // Redirection pour forcer une nouvelle vue → nouveau bean
        return "index?faces-redirect=true";
    }

    // --- Utilitaires ---
    private void appendConversation(String q, String r) {
        conversation
                .append("== User:\n").append(q).append("\n")
                .append("== Assistant:\n").append(r).append("\n\n");
    }

    private void addMsg(FacesMessage.Severity sev, String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(sev, summary, detail));
    }

    // --- Rôles prédéfinis pour la liste déroulante (optionnel si tu utilises un textarea libre) ---
    private List<SelectItem> listeRolesSysteme;
    public List<SelectItem> getRolesSysteme() {
        if (this.listeRolesSysteme == null) {
            this.listeRolesSysteme = new ArrayList<>();

            String role = """
                    You are a helpful assistant. You help the user find information and explain step by step when asked.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Assistant"));

            role = """
                    You are an interpreter. Translate French ↔ English. If 1–3 words, add usage examples.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Traducteur Anglais–Français"));

            role = """
                    You are a travel guide. When given a country/city, list top places to visit and typical meal price.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Guide touristique"));
        }
        return this.listeRolesSysteme;
    }

    // --- Getters/Setters pour la page JSF ---
    public String getRoleSysteme() { return roleSysteme; }
    public void setRoleSysteme(String roleSysteme) { this.roleSysteme = roleSysteme; }

    public boolean isRoleSystemeChangeable() { return roleSystemeChangeable; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getReponse() { return reponse; }
    public void setReponse(String reponse) { this.reponse = reponse; }

    public String getConversation() { return conversation.toString(); }
    public void setConversation(String conversation) { this.conversation = new StringBuilder(conversation); }
}
