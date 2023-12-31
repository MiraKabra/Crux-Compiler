package crux.ir;

import crux.ast.SymbolTable.Symbol;
import crux.ast.*;
import crux.ast.OpExpr.Operation;
import crux.ast.traversal.NodeVisitor;
import crux.ast.types.*;
import crux.ir.insts.*;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class InstPair {
  Instruction start;
  Instruction end;
  LocalVar variable;

  public InstPair(Instruction start, Instruction end, LocalVar variable){
    this.start = start;
    this.end = end;
    this.variable = variable;
  }

  Instruction getStart(){
    return start;
  }

  Instruction getEnd() {
    return end;
  }

  LocalVar getVariable(){
    return variable;
  }
}

/**
 * Convert AST to IR and build the CFG
 */
public final class ASTLower implements NodeVisitor<InstPair> {
  private Program mCurrentProgram = null;
  private Function mCurrentFunction = null;

  private Map<Symbol, LocalVar> mCurrentLocalVarMap = null;
  Stack<Instruction> stack = new Stack<>();
  /**
   * A constructor to initialize member variables
   */
  public ASTLower() {}

  public Program lower(DeclarationList ast) {
    visit(ast);
    return mCurrentProgram;
  }

  @Override
  public InstPair visit(DeclarationList declarationList) {
    mCurrentProgram = new Program();

    for(Node child: declarationList.getChildren()){
      Declaration declaration = (Declaration) child;
      declaration.accept(this);
    }
    return null;
  }

  /**
   * This visitor should create a Function instance for the functionDefinition node, add parameters
   * to the localVarMap, add the function to the program, and init the function start Instruction.
   */
  @Override
  public InstPair visit(FunctionDefinition functionDefinition) {
    mCurrentLocalVarMap = new HashMap<>();
    Symbol symbol = functionDefinition.getSymbol();
    FuncType funcType = (FuncType) symbol.getType();
    mCurrentFunction = new Function(symbol.getName(), funcType);

    List<Symbol> parameters = functionDefinition.getParameters();
    List<LocalVar> listVars = new ArrayList<>();
    //Add all the parameters to local variable map
    for(Symbol param: parameters){
      LocalVar localVar = mCurrentFunction.getTempVar(param.getType(), param.getName());
      listVars.add(localVar);
      mCurrentLocalVarMap.put(param, localVar);
    }
    mCurrentFunction.setArguments(listVars);

    mCurrentProgram.addFunction(mCurrentFunction);

    StatementList statementList = functionDefinition.getStatements();
    InstPair statementsPair = statementList.accept(this);
    mCurrentFunction.setStart(statementsPair.getStart());
    mCurrentFunction = null;
    mCurrentLocalVarMap = null;
    return null;
  }

  @Override
  public InstPair visit(StatementList statementList) {

    List<Node> statements = statementList.getChildren();

    Instruction firstNode = new NopInst();
    Instruction lastNode = firstNode;
    if(statements.size() == 0){
      return new InstPair(firstNode, firstNode, null);
    }
    for(Node statement : statements){
      InstPair instPair = statement.accept(this);
      lastNode.setNext(0, instPair.getStart());
      lastNode = instPair.getEnd();
    }
    return new InstPair(firstNode, lastNode, null);
  }

  /**
   * Declarations, could be either local or Global
   */
  @Override
  public InstPair visit(VariableDeclaration variableDeclaration) {

    Symbol symbol = variableDeclaration.getSymbol();
    //If it has reached here from a function, store as a local variable
    if(mCurrentFunction != null){
      //changed
      LocalVar localVar = mCurrentFunction.getTempVar(symbol.getType());
      mCurrentLocalVarMap.put(symbol, localVar);
      NopInst nopInst = new NopInst();
      return new InstPair(nopInst, nopInst, null);
    }else{
      //else store as global variable
      //For variable declaration, there is only one element. For array declaration,
      //there are array.length() number of elements
      GlobalDecl globalDecl = new GlobalDecl(symbol, IntegerConstant.get(mCurrentProgram, 1));
      mCurrentProgram.addGlobalVar(globalDecl);
    }
    return null;

  }

  /**
   * Create a declaration for array and connected it to the CFG
   */
  @Override
  public InstPair visit(ArrayDeclaration arrayDeclaration) {
    Symbol symbol = arrayDeclaration.getSymbol();

    //Array declaration is only global
    ArrayType arrayType = (ArrayType) symbol.getType();
    GlobalDecl globalDecl = new GlobalDecl(symbol, IntegerConstant.get(mCurrentProgram, arrayType.getExtent()));
    mCurrentProgram.addGlobalVar(globalDecl);
    //changed
    return null;
    //return new InstPair(new NopInst(), new NopInst(), null);
  }

  /**
   * LookUp the name in the map(s). For globals, we should do a load to get the value to load into a
   * LocalVar.
   */
  @Override
  public InstPair visit(VarAccess name) {
    Symbol symbol = name.getSymbol();
    if(mCurrentLocalVarMap.containsKey(symbol)){
      NopInst nop = new NopInst();
      return new InstPair(nop, nop, mCurrentLocalVarMap.get(symbol));
    }else{
      //For global
      AddressVar addressVar = mCurrentFunction.getTempAddressVar(symbol.getType());
      //changed
      LocalVar destVar = mCurrentFunction.getTempVar(name.getType());
      AddressAt addressAt = new AddressAt(addressVar, symbol);
      LoadInst loadInst = new LoadInst(destVar, addressVar);
      addressAt.setNext(0, loadInst);
      return new InstPair(addressAt, loadInst, destVar);
    }
  }

  /**
   * If the location is a VarAccess to a LocalVar, copy the value to it. If the location is a
   * VarAccess to a global, store the value. If the location is ArrayAccess, store the value.
   */
  @Override
  public InstPair visit(Assignment assignment) {

    if(assignment.getLocation() instanceof VarAccess){
      //local
      if(mCurrentLocalVarMap.containsKey(((VarAccess)assignment.getLocation()).getSymbol())){
        LocalVar destVar = mCurrentLocalVarMap.get(((VarAccess)assignment.getLocation()).getSymbol());
        InstPair valuePair = assignment.getValue().accept(this);
        CopyInst copyInst = new CopyInst(destVar, valuePair.getVariable());
        valuePair.getEnd().setNext(0, copyInst);
        return new InstPair(valuePair.getStart(), copyInst, null);
      }else{
        //global
        InstPair valuePair = assignment.getValue().accept(this);
        Symbol symbol = ((VarAccess)assignment.getLocation()).getSymbol();
        AddressVar destVar = mCurrentFunction.getTempAddressVar(((VarAccess)assignment.getLocation()).getType());
        AddressAt addressAt = new AddressAt(destVar, symbol);
        StoreInst storeInst = new StoreInst(valuePair.getVariable(), destVar);

        addressAt.setNext(0, valuePair.getStart());
        valuePair.getEnd().setNext(0, storeInst);

        return new InstPair(addressAt, storeInst, null);
      }
    }else if(assignment.getLocation() instanceof ArrayAccess){
      InstPair valuePair = assignment.getValue().accept(this);
      Symbol symbol = ((ArrayAccess)assignment.getLocation()).getBase();
      //changed
      AddressVar destVar = mCurrentFunction.getTempAddressVar(((ArrayAccess)assignment.getLocation()).getType());

      InstPair indexPair = ((ArrayAccess)assignment.getLocation()).getIndex().accept(this);
      AddressAt addressAt = new AddressAt(destVar, symbol, indexPair.getVariable());

      StoreInst storeInst = new StoreInst(valuePair.getVariable(), destVar);

      //changed
      indexPair.getEnd().setNext(0, addressAt);
      addressAt.setNext(0, valuePair.getStart());
      valuePair.getEnd().setNext(0, storeInst);

      //changed
      return new InstPair(indexPair.getStart(), storeInst, null);
    }

    //Depending on the condition, create a copyInst or storeInst
    return null;
  }

  /**
   * Lower a Call.
   */
  @Override
  public InstPair visit(Call call) {
    Symbol callee = call.getCallee();
    List<Expression> arguments = call.getArguments();
    NopInst nopInst = new NopInst();
    Instruction firstArgumentNode = nopInst;
    Instruction prevArgumentNode = firstArgumentNode;

    List<LocalVar> params = new ArrayList<>();

    for(Expression expression: arguments){
      InstPair instPair = expression.accept(this);
      params.add(instPair.getVariable());
      if(firstArgumentNode == null){
        firstArgumentNode = instPair.getStart();
        prevArgumentNode = instPair.getEnd();
      }else{
        prevArgumentNode.setNext(0, instPair.getStart());
        prevArgumentNode = instPair.getEnd();
      }
    }
    CallInst callInst = null;
    if(((FuncType)callee.getType()).getRet() instanceof VoidType){
      callInst = new CallInst(callee, params);
      prevArgumentNode.setNext(0, callInst);
      return new InstPair(firstArgumentNode, callInst, null);
    }else{
      LocalVar returnVar = mCurrentFunction.getTempVar(callee.getType());
      callInst = new CallInst(returnVar, callee, params);
      prevArgumentNode.setNext(0, callInst);
      return new InstPair(firstArgumentNode, callInst, returnVar);
    }
  }

  /**
   * Handle operations like arithmetics and comparisons. Also handle logical operations (and,
   * or, not).
   */
  @Override
  public InstPair visit(OpExpr operation) {
    Operation op = operation.getOp();

    boolean isCompare = isCompareOperation(op);
    boolean isBinary = isBinaryOperation(op);

    CompareInst.Predicate predicate = null;
    BinaryOperator.Op binaryOp = null;

    if(isCompare){
      if(op == Operation.GE){
        predicate = CompareInst.Predicate.GE;
      }else if(op == Operation.LE){
        predicate = CompareInst.Predicate.LE;
      }else if(op == Operation.NE){
        predicate = CompareInst.Predicate.NE;
      }else if(op == Operation.EQ){
        predicate = CompareInst.Predicate.EQ;
      }else if(op == Operation.GT){
        predicate = CompareInst.Predicate.GT;
      }else if(op == Operation.LT){
        predicate = CompareInst.Predicate.LT;
      }

      InstPair lhs = operation.getLeft().accept(this);
      InstPair rhs = operation.getRight().accept(this);
      LocalVar destVar = mCurrentFunction.getTempVar(new BoolType());
      CompareInst compareInst = new CompareInst(destVar, predicate, lhs.getVariable(), rhs.getVariable());

      lhs.getEnd().setNext(0, rhs.getStart());
      rhs.getEnd().setNext(0, compareInst);

      return new InstPair(lhs.getStart(), compareInst, destVar);
    }
    if(isBinary){
      if(op == Operation.ADD){
        binaryOp = BinaryOperator.Op.Add;
      }else if(op == Operation.SUB){
        binaryOp = BinaryOperator.Op.Sub;
      }else if(op == Operation.MULT){
        binaryOp = BinaryOperator.Op.Mul;
      }else if(op == Operation.DIV){
        binaryOp = BinaryOperator.Op.Div;
      }

      InstPair lhs = operation.getLeft().accept(this);
      InstPair rhs = operation.getRight().accept(this);
      LocalVar destVar = mCurrentFunction.getTempVar(new BoolType());
      BinaryOperator binaryInst = new BinaryOperator(binaryOp, destVar, lhs.getVariable(), rhs.getVariable());

      lhs.getEnd().setNext(0, rhs.getStart());
      rhs.getEnd().setNext(0, binaryInst);

      return new InstPair(lhs.getStart(), binaryInst, destVar);
    }
    else if(op == Operation.LOGIC_AND){
      InstPair lhs = operation.getLeft().accept(this);
      JumpInst jumpInst = new JumpInst(lhs.getVariable());
      LocalVar destVar = mCurrentFunction.getTempVar(new BoolType());
      CopyInst copyInstLeft = new CopyInst(destVar, lhs.getVariable());
      InstPair rhs = operation.getRight().accept(this);
      CopyInst copyInstRight = new CopyInst(destVar, rhs.getVariable());
      NopInst nopInst = new NopInst();

      lhs.getEnd().setNext(0, jumpInst);
      jumpInst.setNext(0, copyInstLeft);
      copyInstLeft.setNext(0, nopInst);

      jumpInst.setNext(1, rhs.getStart());
      rhs.getEnd().setNext(0, copyInstRight);
      copyInstRight.setNext(0, nopInst);

      return new InstPair(lhs.getStart(), nopInst, destVar);

    }else if(op == Operation.LOGIC_OR){
      InstPair lhs = operation.getLeft().accept(this);
      JumpInst jumpInst = new JumpInst(lhs.getVariable());

      InstPair rhs = operation.getRight().accept(this);
      LocalVar destVar = mCurrentFunction.getTempVar(new BoolType());
      CopyInst rhsCopyInst = new CopyInst(destVar, rhs.getVariable());

      CopyInst truCopyInst = new CopyInst(destVar, lhs.getVariable());
      NopInst nopInst = new NopInst();

      lhs.getEnd().setNext(0, jumpInst);
      jumpInst.setNext(0, rhs.getStart());
      rhs.getEnd().setNext(0, rhsCopyInst);
      rhsCopyInst.setNext(0, nopInst);

      jumpInst.setNext(1, truCopyInst);
      truCopyInst.setNext(0, nopInst);

      return new InstPair(lhs.getStart(), nopInst, destVar);

    }else if(op == Operation.LOGIC_NOT){
      InstPair lhs = operation.getLeft().accept(this);
      LocalVar destVar = mCurrentFunction.getTempVar(new BoolType());

      UnaryNotInst unaryNotInst = new UnaryNotInst(destVar, lhs.getVariable());
      lhs.getEnd().setNext(0, unaryNotInst);

      return new InstPair(lhs.getStart(), unaryNotInst, destVar);
    }
    return null;
  }

  private boolean isCompareOperation(Operation op){
    Set<Operation> set = new HashSet<>();
    set.add(Operation.GE);
    set.add(Operation.GT);
    set.add(Operation.LE);
    set.add(Operation.LT);
    set.add(Operation.EQ);
    set.add(Operation.NE);

    if(set.contains(op)) return true;
    return false;
  }

  private boolean isBinaryOperation(Operation op){
    Set<Operation> set = new HashSet<>();
    set.add(Operation.ADD);
    set.add(Operation.SUB);
    set.add(Operation.MULT);
    set.add(Operation.DIV);

    if(set.contains(op)) return true;
    return false;
  }

  private InstPair visit(Expression expression) {
    return null;
  }

  /**
   * It should compute the address into the array, do the load, and return the value in a LocalVar.
   */
  @Override
  public InstPair visit(ArrayAccess access) {
    InstPair index = access.getIndex().accept(this);
    AddressVar addressVar = mCurrentFunction.getTempAddressVar(access.getType());
    AddressAt addressAt = new AddressAt(addressVar, access.getBase(), index.getVariable());
    LocalVar destVar = mCurrentFunction.getTempVar(access.getBase().getType());
    LoadInst loadInst = new LoadInst(destVar, addressVar);
    index.getEnd().setNext(0, addressAt);
    addressAt.setNext(0, loadInst);
    return new InstPair(index.getStart(), loadInst, destVar);
  }

  /**
   * Copy the literal into a tempVar
   */
  @Override
  public InstPair visit(LiteralBool literalBool) {
    BooleanConstant booleanConstant = BooleanConstant.get(mCurrentProgram, literalBool.getValue());
    LocalVar tempVar = mCurrentFunction.getTempVar(new BoolType());
    CopyInst copyInst = new CopyInst(tempVar, booleanConstant);
    return new InstPair(copyInst, copyInst, tempVar);
  }

  /**
   * Copy the literal into a tempVar
   */
  @Override
  public InstPair visit(LiteralInt literalInt) {
    IntegerConstant integerConstant = IntegerConstant.get(mCurrentProgram, literalInt.getValue());
    LocalVar tempVar = mCurrentFunction.getTempVar(new IntType());
    CopyInst copyInst = new CopyInst(tempVar, integerConstant);
    return new InstPair(copyInst, copyInst, tempVar);
  }

  /**
   * Lower a Return.
   */
  @Override
  public InstPair visit(Return ret) {
    InstPair valPair = ret.getValue().accept(this);
    ReturnInst returnInst = new ReturnInst(valPair.getVariable());
    valPair.getEnd().setNext(0, returnInst);

    return new InstPair(valPair.getStart(), returnInst, null);
  }

  /**
   * Break Node
   */
  @Override
  public InstPair visit(Break brk) {
    NopInst nopInst = new NopInst();
    return new InstPair(stack.peek(), nopInst, null);
  }

  /**
   * Implement If Then Else statements.
   */
  @Override
  public InstPair visit(IfElseBranch ifElseBranch) {

    InstPair conditionPair = ifElseBranch.getCondition().accept(this);
    JumpInst jumpInst = new JumpInst(conditionPair.getVariable());
    InstPair elsePair = ifElseBranch.getElseBlock().accept(this);
    InstPair thenPair = ifElseBranch.getThenBlock().accept(this);
    NopInst nopInst = new NopInst();

    conditionPair.getEnd().setNext(0, jumpInst);
    jumpInst.setNext(0, elsePair.getStart());
    jumpInst.setNext(1, thenPair.getStart());
    elsePair.getEnd().setNext(0, nopInst);
    thenPair.getEnd().setNext(0, nopInst);

    return new InstPair(conditionPair.getStart(), nopInst, null);
  }

  /**
   * Implement for loops.
   */
  @Override
  public InstPair visit(For loop) {

    InstPair initPair = loop.getInit().accept(this);
    InstPair condition = loop.getCond().accept(this);
    JumpInst jumpInst = new JumpInst(condition.getVariable());
    NopInst nopInst = new NopInst();
    stack.push(nopInst);
    InstPair body = loop.getBody().accept(this);
    InstPair increment = loop.getIncrement().accept(this);

    initPair.getEnd().setNext(0, condition.getStart());
    condition.getEnd().setNext(0, jumpInst);
    jumpInst.setNext(0, nopInst);
    jumpInst.setNext(1, body.getStart());
    body.getEnd().setNext(0, increment.getStart());
    increment.getEnd().setNext(0, condition.getStart());
    stack.pop();

    return new InstPair(initPair.getStart(), nopInst, null);
  }
}
