package crux.ast.types;

import java.util.Iterator;

/**
 * The field args is a TypeList with a type for each param. The type ret is the type of the function
 * return. The function return could be int, bool, or void. This class should implement the call
 * method.
 */
public final class FuncType extends Type implements java.io.Serializable {
  static final long serialVersionUID = 12022L;

  private TypeList args;
  private Type ret;

  public FuncType(TypeList args, Type returnType) {
    this.args = args;
    this.ret = returnType;
  }

  public Type getRet() {
    return ret;
  }

  public TypeList getArgs() {
    return args;
  }

  @Override
  public String toString() {
    return "func(" + args + "):" + ret;
  }

  @Override
  Type call(Type args){
    if(this.args.equivalent(args)) return ret;
    return super.call(args);
  }

  @Override
  public boolean equivalent(Type that) {
    if(!(that instanceof FuncType)) return false;
    FuncType given = (FuncType) that;
    if(!this.ret.equivalent(given.ret)) return false;
    if(!this.args.equivalent(given.args)) return false;
    return true;
  }
}
