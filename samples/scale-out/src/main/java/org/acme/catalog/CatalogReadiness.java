package org.acme.catalog;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class CatalogReadiness implements HealthCheck {

    private final Catalog catalog;

    public CatalogReadiness(Catalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("catalog").status(catalog.isLoaded()).build();
    }
}
