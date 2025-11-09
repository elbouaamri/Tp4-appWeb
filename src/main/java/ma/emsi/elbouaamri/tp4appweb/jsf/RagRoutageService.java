package ma.emsi.elbouaamri.tp4appweb.jsf;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class RagRoutageService {

    private RetrievalAugmentor augmentor;
    private ContentRetriever retrRag;
    private ContentRetriever retrEmsi;

    @PostConstruct
    void init() {
        try {
            System.out.println("=== Initialisation du routage LM entre rag.pdf et emsi.pdf ===");

            // 🔹 Parser et modèle d'embedding
            var parser = new ApacheTikaDocumentParser();
            EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

            // 🔹 Création des deux retrievers
            retrRag = buildRetriever("rag.pdf", parser, embeddingModel);
            retrEmsi = buildRetriever("emsi.pdf", parser, embeddingModel);

            // 🔹 Modèle Gemini pour le routage
            String apiKey = System.getenv("GEMINI_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("Clé GEMINI_KEY non définie dans l'environnement !");
            }

            ChatModel routerModel = GoogleAiGeminiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName("gemini-2.5-flash")
                    .temperature(0.3)
                    .logRequestsAndResponses(true)
                    .build();

            // 🔹 Map des sources pour le QueryRouter
            Map<ContentRetriever, String> sources = new HashMap<>();
            sources.put(retrRag, "Documents sur le RAG, le fine-tuning et l'intelligence artificielle");
            sources.put(retrEmsi, "Documents sur le management, les filières et les programmes de l'EMSI");

            // 🔹 Routage automatique
            QueryRouter router = new LanguageModelQueryRouter(routerModel, sources);

            // 🔹 Création du RetrievalAugmentor
            augmentor = DefaultRetrievalAugmentor.builder()
                    .queryRouter(router)
                    .build();

            System.out.println("✅ Routage multi-sources RAG/EMSI prêt !");
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'initialisation du RAG multi-sources", e);
        }
    }

    public RetrievalAugmentor getAugmentor() {
        return augmentor;
    }

    /** Permet à PasDeRagService de choisir explicitement un retriever */
    public ContentRetriever getRetrieverByName(String fileName) {
        if (fileName.toLowerCase().contains("emsi")) {
            return retrEmsi;
        } else {
            return retrRag;
        }
    }

    // 🔹 Construction d'un retriever à partir d’un PDF
    private ContentRetriever buildRetriever(String fileName, ApacheTikaDocumentParser parser, EmbeddingModel model) {
        try {
            URL res = getClass().getClassLoader().getResource(fileName);
            if (res == null) throw new RuntimeException("Fichier non trouvé : " + fileName);

            var path = Paths.get(res.toURI());
            Document doc = FileSystemDocumentLoader.loadDocument(path, parser);

            var splitter = DocumentSplitters.recursive(300, 30);
            var segments = splitter.split(doc);

            List<Embedding> embs = model.embedAll(segments).content();
            EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
            store.addAll(embs, segments);

            System.out.println("📄 Retriever construit pour " + fileName + " (" + segments.size() + " segments)");
            return EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(store)
                    .embeddingModel(model)
                    .maxResults(2)
                    .minScore(0.5)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors du chargement du PDF : " + fileName, e);
        }
    }
}