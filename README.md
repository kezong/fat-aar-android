fork from : https://github.com/kezong/fat-aar-android

extra support :

>FatAarExtension.groovy
```
  /**
    * support delete sub aar <declare-style/> , avoid bundle aar failed
    * */
  public HashMap<String, HashSet<String>> excludeDeclareStyleAttrs = new HashMap<>()

  /**
    * support delete sub aar <application/> attribute, avoid bundle aar failed
    * */
  public List<String> excludeApplicationAttr = new ArrayList<>()

  /**
    * support delete sub aar so, avoid bundle aar failed
    * */
  public List<String> abiFilter = new ArrayList<>()

  /**
    * support delete duplicate sub aar so, avoid bundle aar failed
    * */
  public HashMap<String, HashSet<String>> excludeSos = new HashMap<>()


  /**
    * support exclude classes, avoid class duplicated
    * */
  public List<String> excludeClasses = new ArrayList<>()

```