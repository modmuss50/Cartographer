package cartographer;

import cuchaz.enigma.analysis.ParsedJar;
import cuchaz.enigma.mapping.entry.MethodDefEntry;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Util {

	public static String translate(MethodDefEntry methodEntry) {
		String className = methodEntry.getOwnerClassEntry().getClassName();
		String desc = methodEntry.getDesc().toString();
		return className + "." + methodEntry.getName() + desc;
	}

	//Wrappers around classNode and java reflection
	public static class ClassData {

		public String name;

		public ClassData superClass;

		public List<ClassData> interfaces;

		public List<MethodData> methods = new ArrayList<>();

		public ClassData(ClassNode classNode, ParsedJar jar, LibraryProvider libraryProvider) {
			this.name = classNode.name;
			//First we try and grab the class from the provided mc jar
			ClassNode superClassNode = jar.getClassNode(classNode.superName);
			if (superClassNode == null && libraryProvider != null) {
				//Get the class form a dep
				superClassNode = libraryProvider.getClassNode(classNode.superName);
			}
			if (superClassNode == null) {
				try {
					//Check to see if the classpath has it
					this.superClass = new ClassData(classNode.superName);
				} catch (ClassNotFoundException e) {
					throw new RuntimeException("Failed to find class", e);
				}
			} else {
				this.superClass = new ClassData(superClassNode, jar, libraryProvider);
			}
			methods.addAll(
				classNode.methods.stream()
					.map(MethodData::new)
					.collect(Collectors.toList())
			);

			if (!classNode.interfaces.isEmpty()) {
				interfaces = new ArrayList<>();
				for (String name : classNode.interfaces) {
					ClassData interfaceData;
					ClassNode interfaceNode = jar.getClassNode(name);
					if (interfaceNode == null) {
						interfaceNode = libraryProvider.getClassNode(name);
					}
					if (interfaceNode == null) {
						try {
							interfaceData = new ClassData(name);
						} catch (Exception e) {
							throw new RuntimeException("Failed to load info for " + name, e);
						}
					} else {
						interfaceData = new ClassData(interfaceNode, jar, libraryProvider);
					}
					Validate.notNull(interfaceData);
					interfaces.add(interfaceData);
				}
			}

		}

		public ClassData(Class clazz) {
			this.name = clazz.getName();
			if (!clazz.getName().equals(Object.class.getName()) && clazz.getSuperclass() != null) {
				this.superClass = new ClassData(clazz.getSuperclass());
			}
			methods.addAll(
				Arrays.stream(clazz.getDeclaredMethods())
					.map(MethodData::new)
					.collect(Collectors.toList())
			);
			if (clazz.getInterfaces().length != 0) {
				interfaces = new ArrayList<>();
				interfaces.addAll(Arrays.stream(clazz.getInterfaces())
					.map(ClassData::new)
					.collect(Collectors.toList())
				);
			}
		}

		public ClassData(String className) throws ClassNotFoundException {
			this(Class.forName(className.replaceAll("/", ".")));
		}

		public MethodData getMethodData(MethodDefEntry methodEntry) {
			for (MethodData methodData : methods) {
				if (methodData.name.equals(methodEntry.getName()) && methodData.desc.equals(methodEntry.getDesc().toString())) {
					return methodData;
				}
			}
			if (interfaces != null) {
				for (ClassData iface : interfaces) {
					MethodData md = iface.getMethodData(methodEntry);
					if (md != null) {
						return md;
					}
				}
			}
			if (superClass != null) {
				MethodData md = superClass.getMethodData(methodEntry);
				if (md != null) {
					return md;
				}
			}
			return null;
		}

		public void getAncestors(List<ClassData> list) {
			if (interfaces != null) {
				list.addAll(interfaces);
				interfaces.forEach(classData -> classData.getAncestors(list));
			}
			if (superClass != null) {
				list.add(superClass);
				superClass.getAncestors(list);
			}
		}
	}

	public static class MethodData {

		String name;
		String desc;
		int modifiers;

		public MethodData(MethodNode methodNode) {
			this.name = methodNode.name;
			this.desc = methodNode.desc;
			this.modifiers = methodNode.access;
		}

		public MethodData(Method method) {
			this.name = method.getName();
			this.desc = getMethodDescriptor(method);
			this.modifiers = method.getModifiers();
		}

		public boolean isPrivate(){
			return Modifier.isPrivate(modifiers);
		}

		public boolean isStatic(){
			return Modifier.isStatic(modifiers);
		}

	}

	static String getDescriptorForClass(final Class c) {
		if (c.isPrimitive()) {
			if (c == byte.class)
				return "B";
			if (c == char.class)
				return "C";
			if (c == double.class)
				return "D";
			if (c == float.class)
				return "F";
			if (c == int.class)
				return "I";
			if (c == long.class)
				return "J";
			if (c == short.class)
				return "S";
			if (c == boolean.class)
				return "Z";
			if (c == void.class)
				return "V";
			throw new RuntimeException("Unrecognized primitive " + c);
		}
		if (c.isArray())
			return c.getName().replace('.', '/');
		return ('L' + c.getName() + ';').replace('.', '/');
	}

	static String getMethodDescriptor(Method m) {
		String s = "(";
		for (Class c : m.getParameterTypes()) {
			s += getDescriptorForClass(c);
		}
		s += ')';
		return s + getDescriptorForClass(m.getReturnType());
	}

	//Thanks thiakil
	public static int getObfIndex(String in) {
		if (in == null || in.isEmpty()) {
			return 0;
		}
		int result = 0;
		for (int col = in.length(); col > 0; col--) {
			result += (Math.pow(26, col - 1) * getDigit(in.charAt(in.length() - col)));
		}
		return result;
	}

	//Thanks thiakil
	public static int getDigit(char c) {
		if (c >= 'A' && c <= 'Z') {
			return c - 'A' + 1;
		} else if (c >= 'a' && c <= 'z') {
			return c - 'a' + 1;
		}
		return 0;
	}

	public static boolean checkHash(File file, String hash){
		if(!file.exists()){
			return false;
		}
		try {
			return getSha1(file).equalsIgnoreCase(hash);
		} catch (Exception e) {
			return false;
		}
	}

	//Thanks https://stackoverflow.com/questions/6293713/java-how-to-create-sha-1-for-a-file
	public static String getSha1(File file) throws Exception  {
		MessageDigest digest = MessageDigest.getInstance("SHA-1");
		InputStream fis = new FileInputStream(file);
		int n = 0;
		byte[] buffer = new byte[8192];
		while (n != -1) {
			n = fis.read(buffer);
			if (n > 0) {
				digest.update(buffer, 0, n);
			}
		}
		return digest.toString();
	}

}
