package cartographer;

import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import cuchaz.enigma.mapping.LocalVariableMapping;
import cuchaz.enigma.mapping.MethodMapping;
import cuchaz.enigma.mapping.entry.MethodDefEntry;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//This is needed as normal mappings dont save the names of the constructors
public class ConstructorMapping {

	List<Mapping> mappings = new ArrayList<>();

	public ConstructorMapping() {
	}

	public ConstructorMapping(File file) throws IOException {
		Gson gson = new GsonBuilder().create();
		Type type = new TypeToken<List<Mapping>>(){}.getType();
		mappings = gson.fromJson(FileUtils.readFileToString(file, StandardCharsets.UTF_8), type);
	}

	public Mapping addMapping(MethodDefEntry methodEntry){
		if(!methodEntry.isConstructor()){
			throw new RuntimeException("Method " + methodEntry.toString() + " is not a constructor!");
		}
		Mapping mapping = new Mapping(methodEntry);
		mappings.add(mapping);
		return mapping;
	}

	public Mapping getMapping(MethodDefEntry methodDefEntry){
		return mappings.stream()
			.filter(
					mapping ->
						mapping.desc.equals(methodDefEntry.getDesc().toString()) &&
						mapping.owner.equals(methodDefEntry.getOwnerClassEntry().getName()
					))
			.findFirst()
			.orElse(null);
	}

	public Mapping getMapping(String className, MethodMapping methodMapping){
		return mappings.stream()
			.filter(
				mapping ->
						mapping.desc.equals(methodMapping.getObfDesc().toString()) &&
						mapping.owner.equals(className)
				)
			.findFirst()
			.orElse(null);
	}

	public void save(File file) throws IOException {
		String json = new GsonBuilder().setPrettyPrinting().create().toJson(mappings);
		FileUtils.writeStringToFile(file, json, StandardCharsets.UTF_8);
	}

	public static class Mapping {
		public String obfName;
		public String deobfName;
		public String owner;
		public String desc;
		public Map<Integer, VariableMapping> variableMappings;

		public Mapping(MethodDefEntry methodMapping) {
			this.obfName = methodMapping.getName().replaceAll("<", "").replaceAll(">", "");
			this.owner = methodMapping.getOwnerClassEntry().getName();
			this.desc = methodMapping.getDesc().toString();
			this.variableMappings = Maps.newTreeMap();
		}

		public void apply(MethodMapping methodMapping){
			this.deobfName = methodMapping.getDeobfName();
			methodMapping.arguments().forEach(localVariableMapping -> variableMappings.put(localVariableMapping.getIndex(), new VariableMapping(localVariableMapping)));
		}

		public void applyVariableMappings(MethodMapping methodMapping){
			methodMapping.arguments().forEach(localVariableMapping -> variableMappings.put(localVariableMapping.getIndex(), new VariableMapping(localVariableMapping)));
		}

		public void add(LocalVariableMapping localVariableMapping){
			variableMappings.put(localVariableMapping.getIndex(), new VariableMapping(localVariableMapping));
		}
	}

	public static class VariableMapping {
		int index;
		String name;

		public VariableMapping(int index, String name) {
			this.index = index;
			this.name = name;
		}

		public VariableMapping(LocalVariableMapping localVariableMapping){
			this(localVariableMapping.getIndex(), localVariableMapping.getName());
		}
	}

}
