package top.nintha.anno.processor;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import top.nintha.anno.Data;
import top.nintha.anno.ProcessUtil;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;

import static top.nintha.anno.ProcessUtil.*;

@SupportedAnnotationTypes("top.nintha.anno.Data")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class DataAnnotationProcessor extends BaseProcessor {

    /**
     * 类的语法树节点
     */
    private JCTree.JCClassDecl jcClass;

    /**
     * 字段的语法树节点的集合
     */
    private List<JCTree.JCVariableDecl> fieldJCVariables;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        //首先获取被Data注解标记的元素
        Set<? extends Element> set = roundEnv.getElementsAnnotatedWith(Data.class);

        set.forEach(element -> {

            //获取当前元素的JCTree对象
            JCTree jcTree = trees.getTree(element);

            //JCTree利用的是访问者模式，将数据与数据的处理进行解耦，TreeTranslator就是访问者，这里我们重写访问类时的逻辑
            jcTree.accept(new TreeTranslator() {
                @Override
                public void visitClassDef(JCTree.JCClassDecl jcClass) {
                    messager.printMessage(Diagnostic.Kind.NOTE, "@Data process [" + jcClass.name.toString() + "] begin!");

                    before(jcClass);

                    messager.printMessage(Diagnostic.Kind.NOTE, "@Data process anno size=" + jcClass.mods.annotations.size());
                    JCTree.JCAnnotation toRemove = null;
                    for(JCTree.JCAnnotation anno: jcClass.mods.annotations){
                        messager.printMessage(Diagnostic.Kind.NOTE, "@Data process anno > " + anno.annotationType.toString());
                        if(anno.annotationType.toString().equals(Data.class.getSimpleName())){
                            messager.printMessage(Diagnostic.Kind.NOTE, "@Data process anno remove @Data");
                            toRemove = anno;
                            break;
                        }
                    }
                    jcClass.mods.annotations = List.filter(jcClass.mods.annotations, toRemove);
                    messager.printMessage(Diagnostic.Kind.NOTE, "@Data process anno size=" + jcClass.mods.annotations.size());



                    jcClass.defs = jcClass.defs.appendList(createDataMethods());

                    after();

                    messager.printMessage(Diagnostic.Kind.NOTE, "@Data process [" + jcClass.name.toString() + "] end!");
                }
            });
        });

        return true;
    }

    /**
     * 进行一些初始化工作
     *
     * @param jcClass 类的语法树节点
     */
    private void before(JCTree.JCClassDecl jcClass) {
        this.jcClass = jcClass;
        this.fieldJCVariables = getJCVariables(jcClass);
    }

    /**
     * 进行一些清理工作
     */
    private void after() {
        this.jcClass = null;
        this.fieldJCVariables = null;
    }

    /**
     * 创建get/set方法
     *
     * @return get/set方法的语法树节点集合
     */
    private List<JCTree> createDataMethods() {
        ListBuffer<JCTree> dataMethods = new ListBuffer<>();

        for (JCTree.JCVariableDecl jcVariable : fieldJCVariables) {
            if (!jcVariable.mods.getFlags().contains(Modifier.FINAL)
                    && !hasSetMethod(jcVariable, jcClass)) {
                dataMethods.append(createSetJCMethod(jcVariable));
            }

            if (!hasGetMethod(jcVariable, jcClass)) {
                dataMethods.append(createGetJCMethod(jcVariable));
            }
        }

        return dataMethods.toList();
    }

    /**
     * 根据字段的语法树节点，创建对应的set方法
     *
     * @param jcVariable 字段的语法树节点
     * @return set方法的语法树节点
     */
    private JCTree.JCMethodDecl createSetJCMethod(JCTree.JCVariableDecl jcVariable) {

        ListBuffer<JCTree.JCStatement> jcStatements = new ListBuffer<>();

        //添加语句 " this.xxx = xxx; "
        jcStatements.append(
                treeMaker.Exec(
                        treeMaker.Assign(
                                treeMaker.Select(
                                        treeMaker.Ident(names.fromString(THIS)),
                                        jcVariable.name
                                ),
                                treeMaker.Ident(names.fromString("param"))
                        )
                )
        );

        JCTree.JCBlock jcBlock = treeMaker.Block(
                0 //访问标志
                , jcStatements.toList() //所有的语句
        );

        JCTree.JCVariableDecl param = treeMaker.VarDef(
                treeMaker.Modifiers(Flags.PARAMETER), //访问标志。极其坑爹！！！
                names.fromString("param"), //名字
                jcVariable.vartype, //类型
                null //初始化语句
        );

        return treeMaker.MethodDef(
                treeMaker.Modifiers(Flags.PUBLIC), //访问标志
                names.fromString(fromPropertyNameToSetMethodName(jcVariable.name.toString())), //名字
                treeMaker.TypeIdent(TypeTag.VOID), //返回类型
                List.nil(), //泛型形参列表
                List.of(param), //参数列表
                List.nil(), //异常列表
                jcBlock, //方法体
                null //默认方法（可能是interface中的那个default）
        );
    }

    /**
     * 根据字段的语法树节点，创建对应的get方法的语法树节点
     *
     * @param jcVariable 字段的语法树节点
     * @return get方法的语法树节点
     */
    private JCTree.JCMethodDecl createGetJCMethod(JCTree.JCVariableDecl jcVariable) {
        ListBuffer<JCTree.JCStatement> jcStatements = new ListBuffer<>();

        //添加语句 " return this.xxx; "
        jcStatements.append(
                treeMaker.Return(
                        treeMaker.Select(
                                treeMaker.Ident(names.fromString(ProcessUtil.THIS)),
                                jcVariable.name
                        )
                )
        );

        JCTree.JCBlock jcBlock = treeMaker.Block(
                0 //访问标志
                , jcStatements.toList() //所有的语句
        );

        return treeMaker.MethodDef(
                treeMaker.Modifiers(Flags.PUBLIC), //访问标志
                names.fromString(fromPropertyNameToGetMethodName(jcVariable.name.toString())), //名字
                jcVariable.vartype, //返回类型
                List.nil(), //泛型形参列表
                List.nil(), //参数列表
                List.nil(), //异常列表
                jcBlock, //方法体
                null //默认方法（可能是interface中的那个default）
        );
    }
}

