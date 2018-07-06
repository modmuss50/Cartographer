package cartographer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MinecraftLibProvider extends LibraryProvider {

	public final String mcVersion;

	public File store;

	private List<File> libs;

	public MinecraftLibProvider(String mcVersion) {
		this.mcVersion = mcVersion;
		this.store = new File("mclibs." + mcVersion);
	}

	@Override
	void getLibs(List<File> fileList) {
		fileList.addAll(libs);
	}

	@Override
	public void load() throws IOException {
		String versionManifest = IOUtils.toString(new URL("https://launchermeta.mojang.com/mc/game/version_manifest.json"), StandardCharsets.UTF_8);
		JsonObject manifestObject = new Gson().fromJson(versionManifest, JsonObject.class);
		JsonArray versions = manifestObject.getAsJsonArray("versions");
		String versionURL = null;
		for (int i = 0; i < versions.size(); i++) {
			JsonObject versionObject = versions.get(i).getAsJsonObject();
			if (versionObject.get("id").getAsString().equals(mcVersion)) {
				versionURL = versionObject.get("url").getAsString();
			}
		}
		Validate.notNull(versionURL);
		String versionData = IOUtils.toString(new URL(versionURL), StandardCharsets.UTF_8);
		JsonObject versionObject = new Gson().fromJson(versionData, JsonObject.class);
		JsonArray libArray = versionObject.get("libraries").getAsJsonArray();
		this.libs = new ArrayList<>();
		for (int i = 0; i < libArray.size(); i++) {
			JsonObject lib = libArray.get(i).getAsJsonObject();
			JsonObject artifact = lib.get("downloads").getAsJsonObject().get("artifact").getAsJsonObject();
			String path = artifact.get("path").getAsString();
			File output = new File(store, path.substring(path.lastIndexOf("/")));
			if (!output.exists()) {
				System.out.println("Downloading... " + artifact.get("url").getAsString());
				FileUtils.copyURLToFile(new URL(artifact.get("url").getAsString()), output);
			}

			libs.add(output);
		}
		super.load();
	}
}
