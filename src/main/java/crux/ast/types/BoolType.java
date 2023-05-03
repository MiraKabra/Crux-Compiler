package crux.ast.types;

/**
 * Types for Booleans values This should implement the equivalent methods along with and,or, and not
 * equivalent will check if the param is instance of BoolType
 */
public final class BoolType extends Type implements java.io.Serializable {
  static final long serialVersionUID = 12022L;

  @Override
  public String toString() {
    return "bool";
  }

  @Override
  Type or(Type that){
    if(that.equivalent(new BoolType())){
      return new BoolType();
    }
    return super.or(that);
  }

  @Override
  Type not(){
    return new BoolType();
  }
  @Override
  Type assign(Type that){
    if(that.equivalent(new BoolType())){
      return new BoolType();
    }
    return super.assign(that);
  }
  @Override
  Type compare(Type that){
    if(that.equivalent(new BoolType())){
      return new BoolType();
    }
    return super.compare(that);
  }

  @Override
  public boolean equivalent(Type that) {
    if(that instanceof BoolType) return true;
    return false;
  }
}
