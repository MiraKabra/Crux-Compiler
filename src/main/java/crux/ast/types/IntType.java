package crux.ast.types;

/**
 * Types for Integers values. This should implement the equivalent methods along with add, sub, mul,
 * div, and compare. The method equivalent will check if the param is an instance of IntType.
 */
public final class IntType extends Type implements java.io.Serializable {
  static final long serialVersionUID = 12022L;

  @Override
  public String toString() {
    return "int";
  }

  @Override
  Type add(Type that){
    if(that instanceof IntType) return new IntType();
    else{
      return super.add(that);
    }
  }
  @Override
  Type sub(Type that){
    if(that instanceof IntType) return new IntType();
    else{
      return super.sub(that);
    }
  }
  @Override
  Type mul(Type that){
    if(that instanceof IntType) return new IntType();
    else{
      return super.mul(that);
    }
  }
  @Override
  Type div(Type that){
    if(that instanceof IntType) return new IntType();
    else{
      return super.div(that);
    }
  }
  @Override
  Type compare(Type that){
    if(that instanceof IntType) return new BoolType();
    else{
      return super.compare(that);
    }
  }

  @Override
  Type or(Type that){
    return super.or(that);
  }

  @Override
  Type not(){
    return super.not();
  }

  @Override
  Type and(Type that) {
    return super.and(that);
  }

  @Override
  public boolean equivalent(Type that) {
    if(that instanceof IntType) return true;
    return false;
  }
}
