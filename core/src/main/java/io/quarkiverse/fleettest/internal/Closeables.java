package io.quarkiverse.fleettest.internal;

final class Closeables {

    private Closeables() {
    }

    // Closes all of them whatever the others did; a null is skipped. A failure is suppressed into the exception
    // being handled when there is one; otherwise the first is thrown once everything had its chance, with the
    // rest suppressed into it.
    static void closeAll(Throwable suppressedInto, AutoCloseable... closeables) {
        RuntimeException failure = null;
        for (AutoCloseable closeable : closeables) {
            if (closeable == null) {
                continue;
            }
            try {
                closeable.close();
            } catch (Exception e) {
                if (suppressedInto != null) {
                    suppressedInto.addSuppressed(e);
                } else if (failure == null) {
                    failure = e instanceof RuntimeException unchecked ? unchecked : new RuntimeException(e);
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
