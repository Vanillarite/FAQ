package com.vanillarite.faq.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class SingleCache<T> {
  private static final Integer KEY = 0;

  private final Cache<Integer, T> cache;
  private final Callable<T> supplier;

  public SingleCache(Callable<T> supplier, long duration, TimeUnit unit) {
    this.supplier = supplier;
    this.cache = CacheBuilder.newBuilder().expireAfterWrite(duration, unit).maximumSize(1).build();
  }

  public T invalidateAndGet() {
    invalidate();
    return get();
  }

  public T get() {
    try {
      T value = cache.get(KEY, supplier);
      if (value == null) invalidate();
      return value;
    } catch (ExecutionException e) {
      final Throwable cause = e.getCause();

      // Try to rethrow the actual exception so it's easier to understand
      if (cause == null) throw new RuntimeException(e);
      else if (cause instanceof RuntimeException) throw (RuntimeException) cause;
      else if (cause instanceof Error) throw (Error) cause;
      else throw new RuntimeException("Unexpected error loading item into cache: " + cause.getMessage(), cause);
    }
  }

  public void invalidate() {
    cache.invalidateAll();
  }

  @Override
  public String toString() {
    return "SingleCache{supplier=" + supplier + "}";
  }
}
