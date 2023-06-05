package au.net.causal.hymie;

@FunctionalInterface
public interface ExceptionalSupplier<T, E extends Throwable>
{
    /**
     * Gets a result.
     *
     * @return a result
     *
     * @throws E if an error occurs.
     */
    T get() throws E;
}
