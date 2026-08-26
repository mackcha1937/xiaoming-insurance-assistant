package com.embabel.insurance.xiaoming.retrieval;

import java.util.List;

@FunctionalInterface
public interface SearchBackend {

    List<SearchHit> search(String query, SearchFilter filter, int topK);
}
