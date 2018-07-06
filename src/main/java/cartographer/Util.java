package cartographer;

import cuchaz.enigma.analysis.ParsedJar;
import cuchaz.enigma.mapping.entry.MethodDefEntry;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Method;
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
		}

		public ClassData(Class clazz) {
			this.name = clazz.getName();
			if (!clazz.getName().equals(Object.class.getName())) {
				this.superClass = new ClassData(clazz.getSuperclass());
			}
			methods.addAll(
				Arrays.stream(clazz.getMethods())
					.map(MethodData::new)
					.collect(Collectors.toList())
			);
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
			return null;
		}

	}

	public static class MethodData {

		String name;
		String desc;

		public MethodData(MethodNode methodNode) {
			this.name = methodNode.name;
			this.desc = methodNode.desc;
		}

		public MethodData(Method method) {
			this.name = method.getName();
			this.desc = getMethodDescriptor(method);
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

}
