package au.net.causal.hymie.controlui;

import picocli.CommandLine.IVersionProvider;

import java.util.Objects;

public abstract class ManifestVersionProvider implements IVersionProvider
{
    private final Package pkg;

    protected ManifestVersionProvider(Package pkg)
    {
        this.pkg = Objects.requireNonNull(pkg);
    }

    protected ManifestVersionProvider(Class<?> clazz)
    {
        this(clazz.getPackage());
    }

    /**
     * @return the version to return when it cannot be looked up from the manifest metadata.
     */
    protected String unknownVersionString()
    {
        return "(no version information)";
    }

    public String[] getVersion() throws Exception
    {
        String version = pkg.getImplementationVersion();
        if (version == null)
            version = unknownVersionString();

        return new String[] {version};
    }
}
