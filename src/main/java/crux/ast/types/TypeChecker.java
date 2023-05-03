package crux.ast.types;

import crux.ast.SymbolTable.Symbol;
import crux.ast.*;
import crux.ast.traversal.NullNodeVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class will associate types with the AST nodes from Stage 2
 */
public final class TypeChecker {
  private Map<Node, Type> nodeTypeMap;
  private final ArrayList<String> errors = new ArrayList<>();
  private Stack<Integer> root = new Stack<>();

  public TypeChecker(){
    nodeTypeMap = new HashMap<>();
  }
  public ArrayList<String> getErrors() {
    return errors;
  }

  public void check(DeclarationList ast) {
    var inferenceVisitor = new TypeInferenceVisitor();
    inferenceVisitor.visit(ast);
  }

  /**
   * Helper function, should be used to add error into the errors array
   */
  private void addTypeError(Node n, String message) {
    errors.add(String.format("TypeError%s[%s]", n.getPosition(), message));
  }

  /**
   * Helper function, should be used to record Types if the Type is an ErrorType then it will call
   * addTypeError
   */
  private void setNodeType(Node n, Type ty) {
    ((BaseNode) n).setType(ty);
    if (ty.getClass() == ErrorType.class) {
      var error = (ErrorType) ty;
      addTypeError(n, error.getMessage());
    }
  }

  /**
   * Helper to retrieve Type from the map
   */
  public Type getType(Node n) {
    return ((BaseNode) n).getType();
  }


  /**
   * This calls will visit each AST node and try to resolve it's type with the help of the
   * symbolTable.
   */
  private final class TypeInferenceVisitor extends NullNodeVisitor<Void> {
    @Override
    public Void visit(VarAccess varAccess) {
      Symbol symbol = varAccess.getSymbol();
      setNodeType(varAccess, symbol.getType());
      return null;
    }

    @Override
    public Void visit(ArrayDeclaration arrayDeclaration) {
      Symbol symbol = arrayDeclaration.getSymbol();
      setNodeType(arrayDeclaration, symbol.getType());
      return null;
    }

    @Override
    public Void visit(Assignment assignment) {
      return null;
    }

    @Override
    public Void visit(Break brk) {
      return null;
    }

    @Override
    public Void visit(Call call) {
      return null;
    }

    @Override
    public Void visit(DeclarationList declarationList) {
      List<Node> declarations;
      declarations = declarationList.getChildren();
      for(Node node : declarations){
        Declaration declaration = (Declaration) node;
        declaration.accept(this);
      }
      return null;
    }

    @Override
    public Void visit(FunctionDefinition functionDefinition) {
      Symbol symbol = functionDefinition.getSymbol();
      List<Symbol> parameters = functionDefinition.getParameters();
      StatementList statementList = functionDefinition.getStatements();
      FuncType currFuncType = (FuncType) symbol.getType();
      boolean return_required = false;
      if(!currFuncType.getRet().equivalent(new VoidType())){
        return_required = true;
      }
      if(return_required){
        root.push(0);
      }
      statementList.accept(this);
      if(return_required && root.pop() > 0){
        addTypeError(functionDefinition, "error: Not returning at all possible paths.");
      }
      return null;
    }

    @Override
    public Void visit(IfElseBranch ifElseBranch) {
      OpExpr cond = (OpExpr) ifElseBranch.getCondition();
      Type type = ((BaseNode)cond.getLeft()).getType().compare(((BaseNode)cond.getRight()).getType());
      if(!(new BoolType().equivalent(type))){
        addTypeError(ifElseBranch, "the condition of if else should be of type boolean");
      }
      ifElseBranch.getThenBlock().accept(this);
      ifElseBranch.getElseBlock().accept(this);
      return null;
    }

    @Override
    public Void visit(ArrayAccess access) {
      return null;
    }

    @Override
    public Void visit(LiteralBool literalBool) {
      return null;
    }

    @Override
    public Void visit(LiteralInt literalInt) {
      return null;
    }

    @Override
    public Void visit(For forloop) {
      return null;
    }

    @Override
    public Void visit(OpExpr op) {
      return null;
    }

    @Override
    public Void visit(Return ret) {
      return null;
    }

    @Override
    public Void visit(StatementList statementList) {
      root.push(0);
      List<Node> list = statementList.getChildren();
      boolean return_included = false;
      for(Node node: list){
        Statement statement = (Statement) node;
        statement.accept(this);
        if(is_return_type(statement)){
          return_included = true;
        }
      }
      root.pop();
      if(!return_included){
        int temp = root.pop();
        root.push(temp+1);
      }
      return null;
    }
    private boolean is_return_type(Statement statement){
      if(statement instanceof Return) return true;
      return false;
    }
    @Override
    public Void visit(VariableDeclaration variableDeclaration) {
      Symbol symbol = variableDeclaration.getSymbol();
      setNodeType(variableDeclaration, symbol.getType());
      return null;
    }
  }
}
