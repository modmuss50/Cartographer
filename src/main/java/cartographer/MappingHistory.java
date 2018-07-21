package cartographer;

import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class MappingHistory {

	public static MappingHistory newMappingsHistory() {
		MappingHistory history = new MappingHistory();
		history.classes = new ArrayList<>();
		history.methods = new ArrayList<>();
		history.fields = new ArrayList<>();
		history.args = new ArrayList<>();
		return history;
	}

	public static MappingHistory readHistory(File file) throws IOException {
		MappingHistory history = newMappingsHistory();
		BufferedReader reader = Files.newBufferedReader(file.toPath());
		String line;
		while ((line = reader.readLine()) != null) {
			if (line.isEmpty()) {
				continue;
			}
			if (line.startsWith("CLASS")) {
				String[] split = line.split("\t");
				NamedEntry entry = new NamedEntry(split[1], Type.CLASS);
				history.classes.add(entry);
			}
			if (line.startsWith("METHOD")) {
				String[] split = line.split("\t");
				SignatureEntry entry = new SignatureEntry(split[1], split[2], Type.METHOD);
				history.methods.add(entry);
			}
			if (line.startsWith("ARG")) {
				String[] split = line.split("\t");
				NamedEntry entry = new NamedEntry(split[1], Type.ARG);
				history.args.add(entry);
			}
			if (line.startsWith("FIELD")) {
				String[] split = line.split("\t");
				SignatureEntry entry = new SignatureEntry(split[1], split[2], Type.FIELD);
				history.fields.add(entry);
			}
		}
		return history;
	}

	public List<NamedEntry> classes;
	public List<SignatureEntry> fields;
	public List<SignatureEntry> methods;
	public List<NamedEntry> args;

	public String generateClassName() {
		String newClassName = "class_" + classes.size();
		NamedEntry newClassEntry = new NamedEntry(newClassName, Type.CLASS);
		classes.add(newClassEntry);
		return newClassName;
	}

	public String generateMethodName(String signature) {
		String newMethodName = "method_" + methods.size();
		SignatureEntry newMethodEntry = new SignatureEntry(newMethodName, signature, Type.METHOD);
		methods.add(newMethodEntry);
		return newMethodName;
	}

	public String generateArgName(String name) {
		int method = Integer.parseInt(name.substring(name.indexOf("_") + 1));
		List<NamedEntry> existingNames = findArgMappingsForMethod(method);
		String newArgName = "param_" + method + "_" + existingNames.size();
		NamedEntry newArgEntry = new NamedEntry(newArgName, Type.ARG);
		args.add(newArgEntry);
		return newArgName;
	}

	public String generateFieldName(String signature) {
		String newFieldName = "field_" + fields.size();
		SignatureEntry newFieldEntry = new SignatureEntry(newFieldName, signature, Type.FIELD);
		fields.add(newFieldEntry);
		return newFieldName;
	}

	public void writeToFile(File file) throws IOException {
		StringJoiner output = new StringJoiner("\n");
		classes.forEach(namedEntry -> output.add(namedEntry.toString()));
		methods.forEach(namedEntry -> output.add(namedEntry.toString()));
		fields.forEach(namedEntry -> output.add(namedEntry.toString()));
		args.forEach(namedEntry -> output.add(namedEntry.toString()));
		FileUtils.writeStringToFile(file, output.toString(), Charsets.UTF_8);
	}

	private List<NamedEntry> findArgMappingsForMethod(int method) {
		return args.stream()
			.filter((Predicate<NamedEntry>) input -> input.name.startsWith("param_" + method + "_"))
			.collect(Collectors.toList());
	}

	public static class SignatureEntry extends NamedEntry {
		String signature;

		public SignatureEntry(String name, String signature, Type type) {
			super(name, type);
			this.signature = signature;
		}

		@Override
		public String toString() {
			return type + "\t" + name + "\t" + signature;
		}
	}

	public static class NamedEntry extends BaseEntry {
		String name; //name + signature

		public NamedEntry(String name, Type type) {
			super(type);
			this.name = name;
		}

		@Override
		public String toString() {
			return type + "\t" + name;
		}
	}

	public static class BaseEntry {
		Type type;

		public BaseEntry(Type type) {
			this.type = type;
		}
	}

	public enum Type {
		CLASS,
		METHOD,
		ARG,
		FIELD
	}

}
