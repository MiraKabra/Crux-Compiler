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
      //Can be a=4 type Or no arr[9]=10 type
      assignment.getLocation().accept(this);
      Expression value = assignment.getValue();
      value.accept(this);
      Type locationType = ((BaseNode)assignment.getLocation()).getType();
      Type type = locationType.assign(((BaseNode)value).getType());
      setNodeType(assignment, type);
      return null;
    }

    @Override
    public Void visit(Break brk) {
      setNodeType(brk, new VoidType());
      return null;
    }

    @Override
    public Void visit(Call call) {
      Symbol symbol = call.getCallee();
      FuncType funcType = (FuncType) symbol.getType();
      TypeList requiredArgType = funcType.getArgs();
      TypeList callList = new TypeList();
      for(Expression expression : call.getArguments()){
        expression.accept(this);
        callList.append(((BaseNode)expression).getType());
      }
      setNodeType(call, funcType.call(callList));
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

      if(symbol.getName().equals("main")){
        if(return_required){
          addTypeError(functionDefinition, "main function need to be type void");
          return null;
        }
        if(parameters.size() != 0){
          addTypeError(functionDefinition, "main function should have zero arguments");
          return null;
        }
      }
      root.push(0);
      statementList.accept(this);
      if(return_required){
        for(Node statement: statementList.getChildren()){
          if(statement instanceof Return){
            if(!((Return) statement).getType().equivalent(currFuncType.getRet())){
              addTypeError(statement, "Not returning the correct type");
            }
          }
        }
      }

      if(return_required && root.pop() > 0){
        addTypeError(functionDefinition, "error: Not returning at all possible paths.");
      }
      return null;
    }

    @Override
    public Void visit(IfElseBranch ifElseBranch) {
      Expression cond = ifElseBranch.getCondition();
      cond.accept(this);
      if(!(new BoolType().equivalent(((BaseNode)cond).getType()))){
        addTypeError(ifElseBranch, "the condition of if else should be of type boolean");
      }
      ifElseBranch.getThenBlock().accept(this);
      ifElseBranch.getElseBlock().accept(this);
      return null;
    }

    @Override
    public Void visit(ArrayAccess access) {
      ArrayType arrayType =(ArrayType) access.getBase().getType();
      access.getIndex().accept(this);

      setNodeType(access, arrayType.index(((BaseNode)access.getIndex()).getType()));
      return null;
    }

    @Override
    public Void visit(LiteralBool literalBool) {
      setNodeType(literalBool, new BoolType());
      //literalBool.setType(new BoolType());
      return null;
    }

    @Override
    public Void visit(LiteralInt literalInt) {
      setNodeType(literalInt, new IntType());
      //literalInt.setType(new IntType());
      return null;
    }

    @Override
    public Void visit(For forloop) {
      Assignment assignment = forloop.getInit();
      assignment.accept(this);

      Expression condition = forloop.getCond();
      condition.accept(this);
      if(!(new BoolType().equivalent(((BaseNode)condition).getType()))){
        addTypeError(forloop, "the condition of for loop should be of type boolean");
      }
      Assignment increment = forloop.getIncrement();
      increment.accept(this);
      StatementList statementList = forloop.getBody();
      statementList.accept(this);
      return null;
    }

    @Override
    public Void visit(OpExpr op) {
      op.getLeft().accept(this);
      if(op.getRight() != null){
        op.getRight().accept(this);
      }
      Type resultType = new BoolType();
      switch (op.getOp()){
        case GE:
        case LE:
        case NE:
        case EQ:
        case GT:
        case LT:
          //This will either result in bool type or error type. so dont even need
          // to check equivalence with bool type
          resultType = ((BaseNode)op.getLeft()).getType().compare(((BaseNode)op.getRight()).getType());
          setNodeType(op, resultType);
          break;
        case ADD:
          resultType = ((BaseNode)op.getLeft()).getType().add(((BaseNode)op.getRight()).getType());
          setNodeType(op, resultType);
          break;
        case SUB:
          resultType = ((BaseNode)op.getLeft()).getType().sub(((BaseNode)op.getRight()).getType());
          setNodeType(op, resultType);
          break;
        case MULT:
          resultType = ((BaseNode)op.getLeft()).getType().mul(((BaseNode)op.getRight()).getType());
          setNodeType(op, resultType);
          break;
        case DIV:
          resultType = ((BaseNode)op.getLeft()).getType().div(((BaseNode)op.getRight()).getType());
          setNodeType(op, resultType);
          break;
        case LOGIC_AND:
          resultType = ((BaseNode)op.getLeft()).getType().and(((BaseNode)op.getRight()).getType());
          setNodeType(op, resultType);
          break;
        case LOGIC_OR:
          resultType = ((BaseNode)op.getLeft()).getType().or(((BaseNode)op.getRight()).getType());
          setNodeType(op, resultType);
          break;
        case LOGIC_NOT:
          resultType = ((BaseNode)op.getLeft()).getType().not();
          setNodeType(op, resultType);
          break;
      }
      return null;
    }

    @Override
    public Void visit(Return ret) {
      Expression expression = ret.getValue();
      expression.accept(this);
      setNodeType(ret, ((BaseNode)expression).getType());
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
      int temp = root.pop() + (return_included? 0 : 1);

      if(!return_included && temp > 0){
        root.push(root.pop() + 1);
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
