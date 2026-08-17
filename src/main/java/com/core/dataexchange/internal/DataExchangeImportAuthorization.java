package com.core.dataexchange.internal;

@FunctionalInterface
public interface DataExchangeImportAuthorization {

    void authorize(int createCount, int updateCount);
}