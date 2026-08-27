package com.embabel.insurance.xiaoming.config;

import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersionRegistry;
import com.embabel.insurance.xiaoming.knowledge.SemanticChunker;
import com.embabel.insurance.xiaoming.knowledge.importer.KnowledgeSnapshotStore;
import com.embabel.insurance.xiaoming.knowledge.importer.Product36743KnowledgeImportService;
import com.embabel.insurance.xiaoming.knowledge.importer.Product36743ReleaseValidator;
import com.embabel.insurance.xiaoming.knowledge.importer.Product36743SourceImporter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(XiaomingKnowledgeImportProperties.class)
public class XiaomingKnowledgeImportConfiguration {

    @Bean
    public Product36743SourceImporter product36743SourceImporter(SemanticChunker chunker) {
        return new Product36743SourceImporter(chunker);
    }

    @Bean
    public Product36743ReleaseValidator product36743ReleaseValidator() {
        return new Product36743ReleaseValidator();
    }

    @Bean
    public KnowledgeSnapshotStore knowledgeSnapshotStore() {
        return new KnowledgeSnapshotStore();
    }

    @Bean
    public Product36743KnowledgeImportService product36743KnowledgeImportService(
            Product36743SourceImporter importer,
            Product36743ReleaseValidator validator,
            KnowledgeVersionRegistry registry,
            KnowledgeSnapshotStore snapshotStore
    ) {
        return new Product36743KnowledgeImportService(importer, validator, registry, snapshotStore);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnProperty(prefix = "xiaoming.knowledge-import", name = "enabled", havingValue = "true")
    public ApplicationRunner product36743KnowledgeImportRunner(
            XiaomingKnowledgeImportProperties properties,
            Product36743KnowledgeImportService importService
    ) {
        return ignored -> {
            if (properties.sourceDirectory() == null || properties.sourceDirectory().isBlank()) {
                throw new IllegalStateException("xiaoming.knowledge-import.source-directory is required when enabled");
            }
            importService.importAndPublish(
                    Path.of(properties.sourceDirectory()),
                    properties.spreadsheetScanMode()
            );
        };
    }
}
