package com.rxhttp.compiler

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.MethodSpec
import rxhttp.wrapper.annotation.Converter
import java.util.*
import javax.lang.model.element.Modifier
import javax.lang.model.element.VariableElement

class ConverterAnnotatedClass {

    private val mElementMap = LinkedHashMap<String, VariableElement>()

    fun add(variableElement: VariableElement) {
        val annotation = variableElement.getAnnotation(Converter::class.java)
        var name = annotation.name
        if (name.isEmpty()) {
            name = variableElement.simpleName.toString()
        }
        mElementMap[name] = variableElement
    }

    val methodList: List<MethodSpec>
        get() {
            val methodList = ArrayList<MethodSpec>()
            for ((key, value) in mElementMap) {
                methodList.add(MethodSpec.methodBuilder("set$key")
                    .addModifiers(Modifier.PUBLIC)
                    .addStatement("""
                            if (${"$"}T.${"$"}L == null)
                            throw new IllegalArgumentException("converter can not be null");
                    """.trimIndent(), ClassName.get(value.enclosingElement.asType()), value.simpleName.toString())
                    .addStatement("this.converter = \$T.\$L",
                        ClassName.get(value.enclosingElement.asType()),
                        value.simpleName.toString())
                    .addStatement("return (R)this")
                    .returns(r)
                    .build()
                )
            }
            methodList.add(
                MethodSpec.methodBuilder("setConverter")
                    .addJavadoc("给Param设置转换器，此方法会在请求发起前，被RxHttp内部调用\n")
                    .addModifiers(Modifier.PRIVATE)
                    .addParameter(p, "param")
                    .addStatement("param.tag(IConverter.class,converter)")
                    .addStatement("return (R)this")
                    .returns(r)
                    .build())
            return methodList
        }
}