package cartographer;

import cuchaz.enigma.Deobfuscator;
import cuchaz.enigma.mapping.*;
import cuchaz.enigma.mapping.entry.ClassEntry;
import cuchaz.enigma.mapping.entry.FieldDefEntry;
import cuchaz.enigma.mapping.entry.MethodDefEntry;
import cuchaz.enigma.throwables.IllegalNameException;
import cuchaz.enigma.throwables.MappingConflict;
import cuchaz.enigma.throwables.MappingParseException;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarFile;

public class Cartographer {

	File oldJar;
	File newJar;
	File outputJar;

	private Mappings oldMappings;
	private Matches matches;

	File oldMappingsFile;
	File outputMappingsFile;
	File historyFile;
	File matchesFile;

	String packageName = "net/minecraft";

	private MappingHistory mappingHistory;
	private Deobfuscator deobfuscator;
	private LibraryProvider libraryProvider;

	private boolean simulate = false;

	public void start() throws IOException {
		Validate.notNull(newJar);
		Validate.notNull(outputMappingsFile);
		Validate.notNull(historyFile);
		Validate.isTrue(!packageName.isEmpty());

		deobfuscator = new Deobfuscator(new JarFile(newJar));

		if (historyFile.exists()) {
			System.out.println("Reading history file");
			mappingHistory = MappingHistory.readHistory(historyFile);
		} else {
			mappingHistory = MappingHistory.newMappingsHistory();
		}

		if (oldMappingsFile != null && oldMappingsFile.exists()) {
			System.out.println("Reading old mappings");
			MappingsEnigmaReader mappingReader = new MappingsEnigmaReader();
			try {
				oldMappings = mappingReader.read(oldMappingsFile);
			} catch (MappingParseException e) {
				throw new RuntimeException("Failed to read input mappings", e);
			}
		}

		if (matchesFile != null && matchesFile.exists()) {
			matches = new Matches();
			System.out.println("Reading matches");
			matches.read(matchesFile);
			System.out.println("Found " + matches.classMatches.size() + " matched classes");
			System.out.println("Found " + matches.methodMatches.size() + " matched methods");
			System.out.println("Found " + matches.methodArgMatches.size() + " matched method args");
			System.out.println("Found " + matches.fieldMatches.size() + " matched fields");
		}

		if (libraryProvider != null) {
			System.out.println("Reading libs");
			libraryProvider.load();
		}

		List<ClassEntry> classEntries = new ArrayList<>(deobfuscator.getJarIndex().getObfClassEntries());
		List<MethodDefEntry> methodEntries = new ArrayList<>(deobfuscator.getJarIndex().getObfBehaviorEntries());
		List<FieldDefEntry> fieldEntries = new ArrayList<>(deobfuscator.getJarIndex().getObfFieldEntries());

		classEntries.sort(Comparator.comparingInt(o -> Util.getObfIndex(o.getName())));
		methodEntries.sort(Comparator.comparingInt(o -> Util.getObfIndex(o.getName())));
		fieldEntries.sort(Comparator.comparingInt(o -> Util.getObfIndex(o.getName())));

		System.out.println("Processing classes");
		for (ClassEntry classEntry : classEntries) {
			handleClass(classEntry);
		}

		System.out.println("Processing methods");
		for (MethodDefEntry methodEntry : methodEntries) {
			handleMethod(methodEntry);
		}

		System.out.println("Processing fields");
		for (FieldDefEntry fieldEntry : fieldEntries) {
			handleField(fieldEntry);
		}

		//Destroy this as soon as possible as it can use a lot of ram
		libraryProvider = null;

		System.out.println("Rebuilding method names");

		final AtomicInteger total = new AtomicInteger();

		deobfuscator.rebuildMethodNames(new Deobfuscator.ProgressListener() {
			@Override
			public void init(int totalWork, String title) {
				total.set(totalWork);
			}

			@Override
			public void onProgress(int numDone, String message) {
				int percentage = (numDone * 100) / total.get();
				System.out.print("\r" + numDone + "/" + total.get() + "\t\t" + percentage + "%\t\t" + message);
			}
		});
		System.out.println();

		if (!simulate) {
			System.out.println("Writing history to disk");
			mappingHistory.writeToFile(historyFile);

			System.out.println("Exporting new mappings");
			MappingsEnigmaWriter mappingWriter = new MappingsEnigmaWriter();
			mappingWriter.write(outputMappingsFile, deobfuscator.getMappings(), false);
		}

		//TODO move this out into its own class
		if (outputJar != null) {
			System.out.println("Exporting jar");
			if (outputJar.exists()) {
				outputJar.delete();
			}
			deobfuscator.writeJar(outputJar, new Deobfuscator.ProgressListener() {
				@Override
				public void init(int totalWork, String title) {

				}

				@Override
				public void onProgress(int numDone, String message) {

				}
			});
		}
	}

	private void handleClass(ClassEntry classEntry) {
		if (!classEntry.getClassName().contains("/")) { //Skip everything already in a package
			String match = getClassMatch(classEntry);
			if (match == null) {
				handleNewClass(classEntry);
			} else {
				handleMatchedClass(classEntry, match);
			}

		} else {
			//Add the class to the mappings without a new name
			try {
				deobfuscator.getMappings().addClassMapping(new ClassMapping(classEntry.getClassName()));
			} catch (MappingConflict mappingConflict) {
				throw new RuntimeException("Mappings failed to apply", mappingConflict);
			}
		}
	}

	private void handleNewClass(ClassEntry classEntry) {
		try {
			String newClassName = mappingHistory.generateClassName();
			//System.out.println("NC: " + classEntry.getClassName() + " -> " + newClassName);
			boolean innerClass = classEntry.isInnerClass();
			deobfuscator.getMappings().addClassMapping(new ClassMapping(classEntry.getClassName(), (innerClass ? "" : packageName + "/") + newClassName));
		} catch (MappingConflict mappingConflict) {
			throw new RuntimeException("Mappings failed to apply", mappingConflict);
		}
	}

	private void handleMatchedClass(ClassEntry entry, String oldName) {
		ClassMapping classMapping = oldMappings.getClassByObf(oldName);
		//	System.out.println("MC: " + entry.getClassName() + " -> " + classMapping.getDeobfName());
		try {
			boolean innerClass = entry.isInnerClass();
			deobfuscator.getMappings().addClassMapping(new ClassMapping(entry.getClassName(), (innerClass ? "" : packageName + "/") + classMapping.getDeobfName()));
		} catch (MappingConflict mappingConflict) {
			throw new RuntimeException("Mappings failed to apply", mappingConflict);
		}
	}

	private String getClassMatch(ClassEntry classEntry) {
		if (matches != null) {
			if (matches.classMatches.containsValue(classEntry.getClassName())) {
				String match = matches.classMatches.inverse().get(classEntry.getClassName());
				return match;
			}
		}
		return null;
	}

	private void handleMethod(MethodDefEntry methodEntry) {
		if (!deobfuscator.isObfuscatedIdentifier(methodEntry, true)) {
			return;
		}
		if (methodEntry.getName().toLowerCase().contains("lambda$")) { //Nope
			return;
		}
		if (methodEntry.isConstructor()) {
			return;
		}

		ClassNode ownerClass = getClassNode(methodEntry.getOwnerClassEntry());
		Util.ClassData classData = new Util.ClassData(ownerClass, deobfuscator.getJar(), libraryProvider);
		Util.MethodData lookupMethod = classData.getMethodData(methodEntry);
		Validate.notNull(lookupMethod);

		List<Util.ClassData> ancestors = new ArrayList<>();
		classData.getAncestors(ancestors);

		boolean foundAncestor = false;
		for (Util.ClassData ancestor : ancestors) {
			Util.MethodData ancestorCheck = ancestor.getMethodData(methodEntry);
			if (ancestorCheck != null) {
				foundAncestor = true;
				break;
			}
			//Fuck enums
			if (ancestor.name.equals(Enum.class.getName())) {
				Util.ClassData dummyEnum = new Util.ClassData(DummyEnum.class);
				for (Util.MethodData methodData : dummyEnum.methods) {
					if (methodData.name.equals(methodEntry.getName())) {
						foundAncestor = true;
						break;
					}
				}
			}
		}

		if (foundAncestor) {
			return;
		}

		if (methodEntry.getName().length() > 3) {
			return;
		}

		//We dont want sub method args to be remapped
		if (!deobfuscator.isMethodProvider(methodEntry.getOwnerClassEntry(), methodEntry)) {
			System.out.println("Found in MC jar " + methodEntry.getName() + "-" + methodEntry.getOwnerClassEntry().getClassName());
			return;
		}

		try {
			NameValidator.validateMethodName(methodEntry.getName());
		} catch (IllegalNameException e) {
			return;
		}

		Pair<MethodMapping, MethodMapping> mapping;
		String match = getMethodMatch(methodEntry);
		if (match != null) {
			mapping = handleMatchedMethod(methodEntry, match);
		} else {
			mapping = handleNewMethod(methodEntry);
		}
		handleMethodArgs(methodEntry, mapping);
	}

	private Pair<MethodMapping, MethodMapping> handleNewMethod(MethodDefEntry methodEntry) {
		String signature = methodEntry.getDesc().toString();
		String newMethodName = mappingHistory.generateMethodName(signature);
		//System.out.println("NM: " + methodEntry.getName() + signature + " -> " + newMethodName + signature);

		MethodMapping mapping = new MethodMapping(methodEntry.getName(), methodEntry.getDesc(), newMethodName);
		deobfuscator.getMappings().getClassByObf(methodEntry.getOwnerClassEntry()).addMethodMapping(mapping);
		return Pair.of(null, mapping);
	}

	private Pair<MethodMapping, MethodMapping> handleMatchedMethod(MethodDefEntry methodEntry, String match) {
		String oldClassName = match.substring(0, match.indexOf("."));
		ClassMapping oldClass = oldMappings.getClassByObf(oldClassName);
		String oldMethodDesc = match.substring(match.indexOf("("));
		String s1 = match.substring(match.indexOf(".") + 1);

		String oldMethodName = s1.substring(0, s1.indexOf("("));
		MethodMapping oldMapping = oldClass.getMethodByObf(oldMethodName, new MethodDescriptor(oldMethodDesc));

		String signature = methodEntry.getDesc().toString();

		//	System.out.println("MM: " + methodEntry.getName() + signature + " -> " + oldMapping.getDeobfName());
		MethodMapping mapping = new MethodMapping(methodEntry.getName(), methodEntry.getDesc(), oldMapping.getDeobfName());
		deobfuscator.getMappings().getClassByObf(methodEntry.getOwnerClassEntry()).addMethodMapping(mapping);
		return Pair.of(oldMapping, mapping);
	}

	private String getMethodMatch(MethodDefEntry methodEntry) {
		if (matches != null) {
			String lookup = Util.translate(methodEntry);
			if (matches.methodMatches.containsValue(lookup)) {
				String match = matches.methodMatches.inverse().get(lookup);
				return match;
			}
		}
		return null;
	}

	private void handleMethodArgs(MethodDefEntry methodEntry, Pair<MethodMapping, MethodMapping> methodMappingPair) {
		int arguments = methodEntry.getDesc().getArgumentDescs().size();
		MethodMapping oldMapping = methodMappingPair.getLeft();
		MethodMapping newMapping = methodMappingPair.getRight();

		Map<Integer, LocalVariableMapping> oldArgMappings = new HashMap<>();
		if (oldMapping != null) {
			oldMapping.arguments().forEach(localVariableMapping -> oldArgMappings.put(localVariableMapping.getIndex(), localVariableMapping));
		}

		for (int arg = 0; arg < arguments; arg++) {
			String match = getMethodArgMatch(methodEntry, arg);
			if (match != null && oldMapping != null) {
				int oldArgPos = Integer.parseInt(match.substring(match.indexOf("#") + 1));
				String oldName = oldArgMappings.get(oldArgPos).getName();
				try {
					newMapping.addArgumentMapping(new LocalVariableMapping(arg, oldName));
				} catch (MappingConflict mappingConflict) {
					throw new RuntimeException("Failed to map arg", mappingConflict);
				}
				//System.out.println("\tMP: " + newMapping.getDeobfName() + "_" + arg + " -> " + oldName);
			} else {
				String newName = mappingHistory.generateArgName(newMapping, arg);
				try {
					newMapping.addArgumentMapping(new LocalVariableMapping(arg, newName));
				} catch (MappingConflict mappingConflict) {
					throw new RuntimeException("Failed to map arg", mappingConflict);
				}
				//	System.out.println("\tNP: " + newMapping.getDeobfName() + "_" + arg + " -> " + newName);
			}
		}
	}

	private String getMethodArgMatch(MethodDefEntry methodEntry, int index) {
		if (matches != null) {
			String lookup = Util.translate(methodEntry) + "#" + index;
			if (matches.methodArgMatches.containsValue(lookup)) {
				String match = matches.methodArgMatches.inverse().get(lookup);
				return match;
			}
		}
		return null;
	}

	private void handleField(FieldDefEntry fieldEntry) {
		if (!deobfuscator.isObfuscatedIdentifier(fieldEntry, true)) {
			return;
		}
		if (fieldEntry.getAccess().isSynthetic()) {
			return;
		}
		//TODO this is a horrible way to figure out if it the entry is mapped
		if (fieldEntry.getName().length() > 2) {
			return;
		}
		String match = getFieldMatch(fieldEntry);
		if (match != null) {
			handleFieldMatch(fieldEntry, match);
		} else {
			handleNewField(fieldEntry);
		}
	}

	private void handleNewField(FieldDefEntry fieldEntry) {
		String signature = fieldEntry.getDesc().toString();
		String newFieldName = mappingHistory.generateFieldName(signature);
		//System.out.println("NF: " + fieldEntry.getName() + signature + " -> " + newFieldName);
		deobfuscator.getMappings().getClassByObf(fieldEntry.getOwnerClassEntry()).addFieldMapping(new FieldMapping(fieldEntry.getName(), fieldEntry.getDesc(), newFieldName, Mappings.EntryModifier.UNCHANGED));
	}

	private void handleFieldMatch(FieldDefEntry fieldEntry, String match) {
		String oldClassName = match.substring(0, match.indexOf("."));
		ClassMapping oldClass = oldMappings.getClassByObf(oldClassName);
		String oldFieldDesc = match.substring(match.indexOf(";;") + 2);
		String s1 = match.substring(match.indexOf(".") + 1);
		String oldFieldName = s1.substring(0, s1.indexOf(";;"));
		FieldMapping oldMapping = oldClass.getFieldByObf(oldFieldName, new TypeDescriptor(oldFieldDesc));

		//	System.out.println("MF: " + fieldEntry.getName() + fieldEntry.getDesc() + " -> " + oldMapping.getDeobfName());
		deobfuscator.getMappings().getClassByObf(fieldEntry.getOwnerClassEntry()).addFieldMapping(new FieldMapping(fieldEntry.getName(), fieldEntry.getDesc(), oldMapping.getDeobfName(), Mappings.EntryModifier.UNCHANGED));

	}

	private String getFieldMatch(FieldDefEntry fieldDefEntry) {
		if (matches != null) {
			String className = fieldDefEntry.getOwnerClassEntry().getClassName();
			String lookup = className + "." + fieldDefEntry.getName() + ";;" + fieldDefEntry.getDesc().toString();
			if (matches.fieldMatches.containsValue(lookup)) {
				String match = matches.fieldMatches.inverse().get(lookup);
				return match;
			}
		}
		return null;
	}

	private ClassNode getClassNode(ClassEntry classEntry) {
		return deobfuscator.getJar().getClassNode(classEntry.getName());
	}

	private ClassNode getClassNode(String className) {
		return deobfuscator.getJar().getClassNode(className);
	}

	public Cartographer setNewJar(File newJar) {
		this.newJar = newJar;
		return this;
	}

	public Cartographer setOldJar(File oldJar) {
		this.oldJar = oldJar;
		return this;
	}

	public Cartographer setOldMappingsFile(File oldMappingsFile) {
		this.oldMappingsFile = oldMappingsFile;
		return this;
	}

	public Cartographer setMatchesFile(File matchesFile) {
		this.matchesFile = matchesFile;
		return this;
	}

	public Cartographer setOutputMappingsFile(File outputMappings) {
		this.outputMappingsFile = outputMappings;
		return this;
	}

	public Cartographer setHistoryFile(File historyFile) {
		this.historyFile = historyFile;
		return this;
	}

	public Cartographer resetHistory() {
		historyFile.delete();
		return this;
	}

	public Cartographer setPackageName(String packageName) {
		this.packageName = packageName;
		return this;
	}

	public Cartographer simulate() {
		simulate = true;
		return this;
	}

	public Cartographer setLibraryProvider(LibraryProvider libraryProvider) {
		this.libraryProvider = libraryProvider;
		return this;
	}

	public Cartographer setOutputJar(File outputJar) {
		this.outputJar = outputJar;
		return this;
	}
}
