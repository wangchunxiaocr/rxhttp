package com.rxhttp.compiler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.kspDependencies
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * User: ljx
 * Date: 2020/3/9
 * Time: 17:04
 */
class RxHttpExtensions(private val logger: KSPLogger) {

    private val classTypeName = Class::class.asClassName()

    private val baseRxHttpName = ClassName(rxHttpPackage, "BaseRxHttp")
    private val callFactoryName = ClassName("rxhttp.wrapper", "CallFactory")
    private val toFunList = ArrayList<FunSpec>()
    private val asFunList = ArrayList<FunSpec>()

    //根据@Parser注解，生成asXxx()、toXxx()、toFlowXxx()系列方法
    @KspExperimental
    @KotlinPoetKspPreview
    fun generateRxHttpExtendFun(ksClass: KSClassDeclaration, key: String) {

        //遍历获取泛型类型
        val typeVariableNames = ksClass.typeParameters.map {
            it.toTypeVariableName()
        }

        //遍历构造方法
        for (ksFunction in ksClass.getConstructors()) {

            if (typeVariableNames.isNotEmpty()
                && ksFunction.isPublic()
            ) {
                if (ksFunction.parameters.size == 1
                    && ksFunction.parameters[0].type.getQualifiedName() == "java.lang.reflect.Type[]"
                ) {
                    continue
                }

                //构造方法参数数量等于泛型数量
                if (ksFunction.parameters.size >= typeVariableNames.size) {
                    val allTypeArg = ksFunction.parameters.find {
                        it.type.getQualifiedName() != "java.lang.reflect.Type"
                    } == null
                    if (allTypeArg) continue
                }
            }

            //根据构造方法参数，获取asXxx方法需要的参数
            val parameterList = ArrayList<ParameterSpec>()
            var typeIndex = 0
            val classTypeParams = ksClass.typeParameters.toTypeParameterResolver()
            val functionTypeParams =
                ksFunction.typeParameters.toTypeParameterResolver(classTypeParams)
            ksFunction.parameters.forEach { ksValueParameter ->
                val variableTypeName = ksValueParameter.type.getQualifiedName()
                val parameterSpec =
                    if ("java.lang.reflect.Type" == variableTypeName
                        && typeIndex < typeVariableNames.size
                    ) {  //Type类型参数转Class<T>类型
                        ParameterSpec.builder(
                            ksValueParameter.name?.asString().toString(),
                            classTypeName.parameterizedBy(typeVariableNames[typeIndex++])
                        ).build()
                    } else {
                        ksValueParameter.toKParameterSpec(functionTypeParams)
                    }
                parameterList.add(parameterSpec)
            }

            val modifiers = ArrayList<KModifier>()
            if (typeVariableNames.isNotEmpty()) {
                modifiers.add(KModifier.INLINE)
            }

            var funBody =
                if (typeVariableNames.isEmpty() || ksFunction.isPublic()) {
                    "return asParser(%T${getTypeVariableString(typeVariableNames)}(${
                        getParamsName(parameterList)
                    }))"
                } else {
                    "return asParser(object: %T${getTypeVariableString(typeVariableNames)}(${
                        getParamsName(parameterList)
                    }) {})"
                }

            if (typeVariableNames.isNotEmpty()) {  //对声明了泛型的解析器，生成kotlin编写的asXxx方法
                FunSpec.builder("as$key")
                    .addModifiers(modifiers)
                    .receiver(baseRxHttpName)
                    .addParameters(parameterList)
                    .addStatement(funBody, ksClass.toClassName()) //方法里面的表达式
                    .addTypeVariables(typeVariableNames.getTypeVariableNames())
                    .build()
                    .apply { asFunList.add(this) }
            }

            funBody =
                if (typeVariableNames.isEmpty() || ksFunction.isPublic()) {
                    "return %T(%T${getTypeVariableString(typeVariableNames)}(${
                        getParamsName(parameterList)
                    }))"
                } else {
                    "return %T(object: %T${getTypeVariableString(typeVariableNames)}(${
                        getParamsName(parameterList)
                    }) {})"
                }

            val toParserName = ClassName("rxhttp", "toParser")
            FunSpec.builder("to$key")
                .addOriginatingKSFile(ksClass.containingFile!!)
                .addModifiers(modifiers)
                .receiver(callFactoryName)
                .addParameters(parameterList)
                .addStatement(funBody, toParserName, ksClass.toClassName())  //方法里面的表达式
                .addTypeVariables(typeVariableNames.getTypeVariableNames())
                .build()
                .apply { toFunList.add(this) }
        }
    }


    @KotlinPoetKspPreview
    fun generateClassFile(codeGenerator: CodeGenerator) {
        val t = TypeVariableName("T")
        val k = TypeVariableName("K")
        val v = TypeVariableName("V")

        val launchName = ClassName("kotlinx.coroutines", "launch")
        val progressName = ClassName("rxhttp.wrapper.entity", "Progress")
        val simpleParserName = ClassName("rxhttp.wrapper.parse", "SimpleParser")
        val coroutineScopeName = ClassName("kotlinx.coroutines", "CoroutineScope")

        val p = TypeVariableName("P")
        val r = TypeVariableName("R")
        val wildcard = TypeVariableName("*")
        val bodyParamName =
            ClassName("rxhttp.wrapper.param", "AbstractBodyParam").parameterizedBy(p)
        val rxHttpBodyParamName =
            ClassName(rxHttpPackage, "RxHttpAbstractBodyParam").parameterizedBy(p, r)
        val pBound = TypeVariableName("P", bodyParamName)
        val rBound = TypeVariableName("R", rxHttpBodyParamName)

        val progressSuspendLambdaName = LambdaTypeName.get(
            parameters = arrayOf(progressName),
            returnType = Unit::class.asClassName()
        ).copy(suspending = true)

        val fileBuilder = FileSpec.builder(rxHttpPackage, "RxHttp")

        val rxHttpName =
            ClassName(rxHttpPackage, RXHttp_CLASS_NAME).parameterizedBy(wildcard, wildcard)
        FunSpec.builder("executeList")
            .addModifiers(KModifier.INLINE)
            .receiver(rxHttpName)
            .addTypeVariable(t.copy(reified = true))
            .addStatement("return executeClass<List<T>>()")
            .build()
            .apply { fileBuilder.addFunction(this) }

        FunSpec.builder("executeClass")
            .addModifiers(KModifier.INLINE)
            .receiver(rxHttpName)
            .addTypeVariable(t.copy(reified = true))
            .addStatement("return execute(object : %T<T>() {})", simpleParserName)
            .build()
            .apply { fileBuilder.addFunction(this) }

        if (isDependenceRxJava()) {
            FunSpec.builder("asList")
                .addModifiers(KModifier.INLINE)
                .receiver(baseRxHttpName)
                .addTypeVariable(t.copy(reified = true))
                .addStatement("return asClass<List<T>>()")
                .build()
                .apply { fileBuilder.addFunction(this) }

            FunSpec.builder("asMap")
                .addModifiers(KModifier.INLINE)
                .receiver(baseRxHttpName)
                .addTypeVariable(k.copy(reified = true))
                .addTypeVariable(v.copy(reified = true))
                .addStatement("return asClass<Map<K,V>>()")
                .build()
                .apply { fileBuilder.addFunction(this) }

            FunSpec.builder("asClass")
                .addModifiers(KModifier.INLINE)
                .receiver(baseRxHttpName)
                .addTypeVariable(t.copy(reified = true))
                .addStatement("return asParser(object : %T<T>() {})", simpleParserName)
                .build()
                .apply { fileBuilder.addFunction(this) }

            asFunList.forEach {
                fileBuilder.addFunction(it)
            }
        }

        val deprecatedAnnotation = AnnotationSpec.builder(Deprecated::class)
            .addMember(
                """
                message = "please use 'toFlow(progressCallback)' instead", 
                level = DeprecationLevel.ERROR
            """.trimIndent()
            )
            .build()

        FunSpec.builder("upload")
            .addKdoc(
                """
                调用此方法监听上传进度                                                    
                @param coroutine  CoroutineScope对象，用于开启协程回调进度，进度回调所在线程取决于协程所在线程
                @param progress 进度回调  
                
                
                此方法已废弃，请使用Flow监听上传进度，性能更优，且更简单，如：
                
                ```
                RxHttp.postForm("/server/...")
                    .addFile("file", File("xxx/1.png"))
                    .toFlow<T> {   //这里也可选择你解析器对应的toFlowXxx方法
                        val currentProgress = it.progress //当前进度 0-100
                        val currentSize = it.currentSize  //当前已上传的字节大小
                        val totalSize = it.totalSize      //要上传的总字节大小    
                    }.catch {
                        //异常回调
                    }.collect {
                        //成功回调
                    }
                ```                   
                """.trimIndent()
            )
            .addAnnotation(deprecatedAnnotation)
            .receiver(rxHttpBodyParamName)
            .addTypeVariable(pBound)
            .addTypeVariable(rBound)
            .addParameter("coroutine", coroutineScopeName)
            .addParameter("progressCallback", progressSuspendLambdaName)
            .addCode(
                """
                param.setProgressCallback { progress, currentSize, totalSize ->
                    coroutine.%T { progressCallback(Progress(progress, currentSize, totalSize)) }
                }
                @Suppress("UNCHECKED_CAST")
                return this as R
                """.trimIndent(), launchName
            )
            .returns(r)
            .build()
            .apply { fileBuilder.addFunction(this) }

        val toFlow = MemberName("rxhttp", "toFlow")
        val toFlowProgress = MemberName("rxhttp", "toFlowProgress")
        val onEachProgress = MemberName("rxhttp", "onEachProgress")
        val bodyParamFactory = ClassName("rxhttp.wrapper", "BodyParamFactory")
        val experimentalCoroutinesApi = ClassName("kotlinx.coroutines", "ExperimentalCoroutinesApi")

        toFunList.forEach {
            fileBuilder.addFunction(it)
            val parseName = it.name.substring(2)
            val typeVariables = it.typeVariables
            val arguments = StringBuilder()
            it.parameters.forEach { p ->
                if (KModifier.VARARG in p.modifiers) {
                    arguments.append("*")
                }
                arguments.append(p.name).append(",")
            }
            if (arguments.isNotEmpty()) arguments.deleteCharAt(arguments.length - 1)
            FunSpec.builder("toFlow$parseName")
                .addModifiers(it.modifiers)
                .receiver(callFactoryName)
                .addParameters(it.parameters)
                .addTypeVariables(typeVariables)
                .addStatement(
                    """return %M(to$parseName${getTypeVariableString(typeVariables)}($arguments))""",
                    toFlow
                )
                .build()
                .apply { fileBuilder.addFunction(this) }

            val capacityParam = ParameterSpec.builder("capacity", Int::class)
                .defaultValue("1")
                .build()
            val isInLine = KModifier.INLINE in it.modifiers
            val builder = ParameterSpec.builder("progress", progressSuspendLambdaName)
            if (isInLine) builder.addModifiers(KModifier.NOINLINE)
            FunSpec.builder("toFlow$parseName")
                .addAnnotation(experimentalCoroutinesApi)
                .addModifiers(it.modifiers)
                .receiver(bodyParamFactory)
                .addTypeVariables(typeVariables)
                .addParameters(it.parameters)
                .addParameter(capacityParam)
                .addParameter(builder.build())
                .addCode(
                    """
                    return 
                        %M(to$parseName${getTypeVariableString(typeVariables)}($arguments), capacity)
                            .%M(progress)
                    """.trimIndent(), toFlowProgress, onEachProgress
                )
                .build()
                .apply { fileBuilder.addFunction(this) }

            FunSpec.builder("toFlow${parseName}Progress")
                .addAnnotation(experimentalCoroutinesApi)
                .addModifiers(it.modifiers)
                .receiver(bodyParamFactory)
                .addTypeVariables(typeVariables)
                .addParameters(it.parameters)
                .addParameter(capacityParam)
                .addCode(
                    """return %M(to$parseName${getTypeVariableString(typeVariables)}($arguments), capacity)""",
                    toFlowProgress
                )
                .build()
                .apply { fileBuilder.addFunction(this) }
        }
        val fileSpec = fileBuilder.build()
        val dependencies = fileSpec.kspDependencies(true)
        logger.warn("LJX Extensions ${dependencies.originatingFiles}")
        fileSpec.writeTo(codeGenerator, dependencies)
    }

    private fun getParamsName(parameterSpecs: MutableList<ParameterSpec>): String {
        val paramsName = StringBuilder()
        parameterSpecs.forEachIndexed { index, parameterSpec ->
            if (index > 0) paramsName.append(", ")
            if (KModifier.VARARG in parameterSpec.modifiers) paramsName.append("*")
            paramsName.append(parameterSpec.name)
        }
        return paramsName.toString()
    }

    //获取泛型字符串 比如:<T> 、<K,V>等等
    private fun getTypeVariableString(typeVariableNames: List<TypeVariableName>): String {
        val type = StringBuilder()
        val size = typeVariableNames.size
        for (i in typeVariableNames.indices) {
            if (i == 0) type.append("<")
            type.append(typeVariableNames[i].name)
            type.append(if (i < size - 1) "," else ">")
        }
        return type.toString()
    }
}

//获取泛型对象列表
private fun List<TypeVariableName>.getTypeVariableNames(): List<TypeVariableName> {
    val anyTypeName = Any::class.asTypeName()
    return map {
        val bounds = it.bounds //泛型边界
        if (bounds.isEmpty() || (bounds.size == 1 && bounds[0].toString() == "java.lang.Object")) {
            TypeVariableName(it.name, anyTypeName).copy(reified = true)
        } else {
            it.copy(reified = true)
        }
    }
}