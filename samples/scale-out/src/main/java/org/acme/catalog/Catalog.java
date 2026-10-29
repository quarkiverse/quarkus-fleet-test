package org.acme.catalog;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.runtime.Startup;

/**
 * A catalog served from memory, loaded in the background after startup. Until it is loaded the instance is
 * listening but cannot serve, which is what a readiness probe is for.
 */
@ApplicationScoped
@Startup
public class Catalog {

    private volatile boolean loaded;

    @PostConstruct
    void load() {
        Thread loader = new Thread(() -> {
            try {
                Thread.sleep(2000); // fetching and indexing the catalog
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            loaded = true;
        }, "catalog-loader");
        loader.setDaemon(true);
        loader.start();
    }

    boolean isLoaded() {
        return loaded;
    }
}
