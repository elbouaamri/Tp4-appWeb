package ma.emsi.elbouaamri.tp4appweb.jsf;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class PasDeRagService implements QueryRouter {

    private ChatModel model;

    @Inject
    private RagRoutageService ragRoutageService;

    @PostConstruct
    void init() {
        String apiKey = System.getenv("GEMINI_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Variable d'environnement GEMINI_KEY non définie !");
        }

        this.model = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-2.5-flash")
                .temperature(0.3)
                .logRequestsAndResponses(true)
                .build();

    }

    @Override
    public List<ContentRetriever> route(Query query) {
        String question = """
                Analyse la requête suivante : '%s'
                Indique uniquement la source à utiliser :
                - 'RAG' si la question concerne l’intelligence artificielle, le fine-tuning, les modèles de langage ;
                - 'EMSI' si la question concerne l’école EMSI, ses programmes, ses filières, ses partenariats ou sa structure ;
                - 'AUCUN' si elle n’est pas liée à ces domaines.
                Réponds uniquement par RAG, EMSI ou AUCUN.
                """.formatted(query.text());

        String reponse = model.chat(question).trim().toUpperCase();

        switch (reponse) {
            case "AUCUN":
                System.out.println("🟡 Pas de RAG (réponse du LLM) : " + reponse);
                return Collections.emptyList();
            case "EMSI":
                System.out.println("🟢 Utilisation du retriever EMSI");
                return Collections.singletonList(ragRoutageService.getRetrieverByName("emsi.pdf"));
            case "RAG":
            default:
                System.out.println("🟢 Utilisation du retriever RAG");
                return Collections.singletonList(ragRoutageService.getRetrieverByName("rag.pdf"));
        }
    }
}