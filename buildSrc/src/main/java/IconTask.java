import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

/** Generates launcher icon, splash logo and TV banner with the current version printed on them. */
public abstract class IconTask extends DefaultTask {
    @Input
    public abstract Property<String> getVersionName();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    public void run() throws IOException {
        File out = getOutputDir().get().getAsFile();
        IconGen.generate(out, getVersionName().get());
    }
}
