fork from : https://github.com/kezong/fat-aar-android

额外支持 :

1. 剔除子aar异常定义在`values.xml`中的`<declare-style/>`

>比如子aar定义的`<declare-style/>`和support库冲突了

2. 剔除子aar重复定义的`<application/>` 中的`attr`

3. 剔除子aar中的so，避免so冲突

4. 剔除子aar中指定的class，避免类冲突

5. 支持abiFilter, 保证打出的fataar只有一个jni目录

6. 支持重复的`<declare-style/>`分离，避免fataar打包失败


