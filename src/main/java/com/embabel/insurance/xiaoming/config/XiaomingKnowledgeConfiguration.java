package com.embabel.insurance.xiaoming.config;

import com.embabel.insurance.xiaoming.evidence.EvidenceService;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersionRegistry;
import com.embabel.insurance.xiaoming.knowledge.PageMapService;
import com.embabel.insurance.xiaoming.knowledge.SemanticChunker;
import com.embabel.insurance.xiaoming.retrieval.RrfFusionService;
import com.embabel.insurance.xiaoming.rules.RuleEngine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(XiaomingKnowledgeProperties.class)
public class XiaomingKnowledgeConfiguration {

    @Bean
    public Clock xiaomingKnowledgeClock() {
        return Clock.systemUTC();
    }

    @Bean
    public KnowledgeVersionRegistry knowledgeVersionRegistry(
            XiaomingProductProperties productProperties,
            Clock xiaomingKnowledgeClock
    ) {
        return new KnowledgeVersionRegistry(productProperties, xiaomingKnowledgeClock);
    }

    @Bean
    public PageMapService pageMapService() {
        return new PageMapService();
    }

    @Bean
    public SemanticChunker semanticChunker(XiaomingKnowledgeProperties properties) {
        return new SemanticChunker(
                properties.maxChunkCharacters(),
                properties.chunkOverlapCharacters()
        );
    }

    @Bean
    public RrfFusionService rrfFusionService() {
        return new RrfFusionService();
    }

    @Bean
    public RuleEngine ruleEngine() {
        return new RuleEngine();
    }

    @Bean
    public EvidenceService evidenceService() {
        return new EvidenceService();
    }
}
