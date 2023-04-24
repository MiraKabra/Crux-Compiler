package crux.ast;

import crux.ast.Position;
import crux.ast.types.*;


import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Symbol table will map each symbol from Crux source code to its declaration or appearance in the
 * source. The symbol table is made up of scopes, Each scope is a map which maps an identifier to
 * it's symbol. Scopes are inserted to the table starting from the first scope (Global Scope). The
 * Global scope is the first scope in each Crux program and it contains all the built in functions
 * and names. The symbol table is an ArrayList of scops.
 */
public final class SymbolTable {

  /**
   * Symbol is used to record the name and type of names in the code. Names include function names,
   * global variables, global arrays, and local variables.
   */
  static public final class Symbol implements java.io.Serializable {
    static final long serialVersionUID = 12022L;
    private final String name;
    private final Type type;
    private final String error;

    /**
     *
     * @param name String
     * @param type the Type
     */
    private Symbol(String name, Type type) {
      this.name = name;
      this.type = type;
      this.error = null;
    }

    private Symbol(String name, String error) {
      this.name = name;
      this.type = null;
      this.error = error;
    }

    /**
     *
     * @return String the name
     */
    public String getName() {
      return name;
    }

    /**
     *
     * @return the type
     */
    public Type getType() {
      return type;
    }

    @Override
    public String toString() {
      if (error != null) {
        return String.format("Symbol(%s:%s)", name, error);
      }
      return String.format("Symbol(%s:%s)", name, type);
    }

    public String toString(boolean includeType) {
      if (error != null) {
        return toString();
      }
      return includeType ? toString() : String.format("Symbol(%s)", name);
    }
  }

  private final PrintStream err;
  private final ArrayList<Map<String, Symbol>> symbolScopes = new ArrayList<>();
  private int currScopeIndex = -1;
  private boolean encounteredError = false;

  SymbolTable(PrintStream err) {
    this.err = err;
    //TODO
    //Add the built in function names
    Map<String, Symbol> globalScope = new HashMap<>();
    //globalScope.put("main", new Symbol("main", new FuncType(new TypeList(), new VoidType())));
    globalScope.put("readInt", new Symbol("readInt", new FuncType(new TypeList(), new IntType())));
    globalScope.put("readChar", new Symbol("readChar", new FuncType(new TypeList(), new IntType())));
    globalScope.put("printBool", new Symbol("printBool", new FuncType(new TypeList(new ArrayList<>(Arrays.asList(new BoolType()))), new VoidType())));
    globalScope.put("printInt", new Symbol("printInt", new FuncType(new TypeList(new ArrayList<>(Arrays.asList(new IntType()))), new VoidType())));
    globalScope.put("printChar", new Symbol("printChar", new FuncType(new TypeList(new ArrayList<>(Arrays.asList(new IntType()))), new VoidType())));
    globalScope.put("println", new Symbol("println", new FuncType(new TypeList(), new VoidType())));
    //Add the global scope containing inbuilt functions
    symbolScopes.add(globalScope);
    currScopeIndex++;
  }

  boolean hasEncounteredError() {
    return encounteredError;
  }

  /**
   * Called to tell symbol table we entered a new scope.
   */

  void enter() {
    //TODO
    Map<String, Symbol> newScope = new HashMap<>();
    symbolScopes.add(newScope);
    currScopeIndex++;
  }

  /**
   * Called to tell symbol table we are exiting a scope.
   */

  void exit() {
    //TODO
    symbolScopes.remove(symbolScopes.size() -1);
    currScopeIndex--;
  }

  /**
   * Insert a symbol to the table at the most recent scope. if the name already exists in the
   * current scope that's a declareation error.
   */
  Symbol add(Position pos, String name, Type type) {
    //TODO
    Map<String, Symbol> currScope = symbolScopes.get(currScopeIndex);
    if(currScope.containsKey(name)){
      err.printf("DeclareSymbolError%s[Already defined in this scope %s.]%n", pos, name);
      encounteredError = true;
      return new Symbol(name, "DeclareSymbolError");
    }else{
      Symbol newSymbol = new Symbol(name, type);
      symbolScopes.get(currScopeIndex).put(name, newSymbol);
      return newSymbol;
    }
  }

  /**
   * lookup a name in the SymbolTable, if the name not found in the table it shouold encounter an
   * error and return a symbol with ResolveSymbolError error. if the symbol is found then return it.
   */
  Symbol lookup(Position pos, String name) {
    var symbol = find(name);
    if (symbol == null) {
      err.printf("ResolveSymbolError%s[Could not find %s.]%n", pos, name);
      encounteredError = true;
      return new Symbol(name, "ResolveSymbolError");
    } else {
      return symbol;
    }
  }

  /**
   * Try to find a symbol in the table starting form the most recent scope.
   */
  private Symbol find(String name) {
    //will return null if no symbol found
    return findByIndex(name, currScopeIndex);
  }
  private Symbol findByIndex(String name, int index){
    //If did not find till global scope, then no symbol exists
    if(index == -1) return null;
    if(symbolScopes.get(index).containsKey(name)){
      return symbolScopes.get(index).get(name);
    }else{
      index--;
      Symbol symbol = findByIndex(name, index);
      return symbol;
    }
  }
}
