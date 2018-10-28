package cartographer;

import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import net.fabricmc.weave.CommandFindMappingErrors;

import java.io.File;

public class MappingTests {

    final String version;
    final MinecraftLibProvider minecraftProvider;

    public MappingTests(String version) {
        this.version = version;
        minecraftProvider = new MinecraftLibProvider(version);
    }

    public void test() throws Exception {
        File minecraftJar = minecraftProvider.minecraftJar();
        File tinyMappings = new File("mappings/" + version + ".tiny");

        File testDir = new File("test");
        if(testDir.exists()){
            testDir.delete();
        }
        testDir.mkdir();


        mappingErrros();

        map(minecraftJar, new File(testDir, "intermediary.jar"), tinyMappings, "intermediary", "mojang");
        map(new File(testDir, "intermediary.jar"), new File(testDir, "mojang.jar"), tinyMappings, "intermediary", "mojang");

    }

    public void map(File input, File output, File mappings, String to, String from){
        System.out.println("Mapping jar from " + from + " to " + to);

        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(TinyUtils.createTinyMappingProvider(mappings.toPath(), from, to))
                .build();

        try {
            OutputConsumerPath outputConsumer = new OutputConsumerPath(output.toPath());
            outputConsumer.addNonClassFiles(input.toPath());
            remapper.read(input.toPath());
            remapper.apply(input.toPath(), outputConsumer);
            outputConsumer.finish();
            remapper.finish();
        } catch (Exception e){
            remapper.finish();
            throw new RuntimeException("Failed to remap jar to " + to, e);
        }
    }

    public void mappingErrros() throws Exception {
        new CommandFindMappingErrors().run(new String[]{
                minecraftProvider.minecraftJar().getAbsolutePath(),
                new File("mappings/" + version + ".mappings").getAbsolutePath()
        });
    }
}
