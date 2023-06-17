package crux.backend;

import crux.ast.SymbolTable.Symbol;
import crux.ir.*;
import crux.ir.insts.*;

import java.util.*;

/**
 * Convert the CFG into Assembly Instructions
 */
public final class CodeGen extends InstVisitor {
  private final Program p;
  private final CodePrinter out;
  private int totalVarNum;
  private int slotNum;
  private HashMap<Variable, Integer> stack = new HashMap<>();
  private HashMap<Instruction, String> labels = new HashMap<>();

  public CodeGen(Program p) {
    totalVarNum = 1;
    slotNum = 0;
    this.p = p;
    // Do not change the file name that is outputted or it will
    // break the grader!

    out = new CodePrinter("a.s");
  }

  private Integer getVarFromStack(Variable variable){  // add to varStackMap if doesn't exists
    if(stack.containsKey(variable)){
      return stack.get(variable);
    } else {
      stack.put(variable, totalVarNum*(-8));
      return (-8)*(totalVarNum++);
    }
  }

  private int getStackPosition(Variable variable){
    if(!stack.containsKey(variable)){
      totalVarNum++;
      stack.put(variable, totalVarNum);
    }
    return stack.get(variable);
  }

  /**
   * It should allocate space for globals call genCode for each Function
   */
  public void genCode() {
    //TODO
    for(Iterator<GlobalDecl> global_itr = p.getGlobals(); global_itr.hasNext();){
      GlobalDecl globalDecl = global_itr.next();
      out.printCode(".comm " + globalDecl.getSymbol().getName() + ", " + globalDecl.getNumElement().getValue()*8 + ", 8");
    }

    int[] count = new int[1];

    for(Iterator<Function> func_itr = p.getFunctions(); func_itr.hasNext();){
      Function function = func_itr.next();
      genCode(function, count);
    }
    out.close();
  }

  private void genCode(Function function, int[] count){

    labels = function.assignLabels(count);
    initialSignature(function);
    List<LocalVar> arguments = function.getArguments();
    int extra = 0;
    for(int i = 1; i <= arguments.size(); i++){
      int position = i * (-8);
      stack.put(arguments.get(i-1), position);

      if(i > 6){
        out.printCode("movq " + (extra*8 + 16) + "(%rbp), %r10");
        out.printCode("movq %r10, " + position + "(%rbp)");
        extra++;
      }
      putInRegister(i);
      totalVarNum++;
    }

    dfs(function);
  }

  private void initialSignature(Function function){
    out.printCode(".globl " + function.getName());
    out.printLabel(function.getName() + ":");
    slotNum = function.getNumTempAddressVars() + function.getNumTempVars();
    if(slotNum % 2 != 0){
      slotNum++;
    }
    out.printCode("enter $(8 * " + slotNum + "), $0");
  }

  private void putInRegister(int i){
    switch (i){
      case 1:
        out.printCode("movq %rdi, -8(%rbp)");
        break;
      case 2:
        out.printCode("movq %rsi, -16(%rbp)");
        break;
      case 3:
        out.printCode("movq %rdx, -24(%rbp)");
        break;
      case 4:
        out.printCode("movq %rcx, -32(%rbp)");
        break;
      case 5:
        out.printCode("movq %r8, -40(%rbp)");
        break;
      case 6:
        out.printCode("movq %r9, -48(%rbp)");
        break;
    }
  }

  private void dfs(Function function){
    Stack<Instruction> s = new Stack<>();
    HashSet<Instruction> seen = new HashSet<>();

    s.push(function.getStart());

    while(!s.isEmpty()){
      Instruction instruction = s.pop();
      if(labels.containsKey(instruction)){
        out.printLabel(labels.get(instruction) + ":");
      }

      instruction.accept(this);
      Instruction firstInst = instruction.getNext(0);
      Instruction secondInst = instruction.getNext(1);

      if(secondInst != null && !seen.contains(secondInst)){
        s.push(secondInst);
        seen.add(secondInst);
      }
      if(firstInst == null){
        out.printCode("leave");
        out.printCode("ret");
      }else{
        if(!seen.contains(firstInst)){
          s.push(firstInst);
          seen.add(firstInst);
        }
        if(s.isEmpty() || firstInst != s.peek()){
          out.printCode("jmp " + labels.get(firstInst));
        }
      }
    }
  }

  public void visit(AddressAt i) {
    AddressVar destVar = i.getDst();
    Symbol base = i.getBase();
    LocalVar offset = i.getOffset();

    String varName = base.getName();
    int destVarPosition = getVarFromStack(destVar);
    if(offset == null){
      out.printCode("movq " + varName + "@GOTPCREL(%rip), " + "%r11");
      out.printCode("movq %r11, " + destVarPosition + "(%rbp)");
    }else{
      int offsetVarPosition = getVarFromStack(offset);
      out.printCode("movq " + offsetVarPosition +"(%rbp), " + "%r10");
      out.printCode("imulq $8, %r10");
      out.printCode("movq " + varName + "@GOTPCREL(%rip), " + "%r11");
      out.printCode("addq %r10, %r11");
      out.printCode("movq %r11, " + destVarPosition + "(%rbp)");
    }
  }

  public void visit(BinaryOperator i) {
    LocalVar destVar = i.getDst();
    LocalVar lhs = i.getLeftOperand();
    LocalVar rhs = i.getRightOperand();
    BinaryOperator.Op binaryOp = i.getOperator();

    int destVarPosition = getVarFromStack(destVar);
    int lhsVarPosition = getVarFromStack(lhs);
    int rhsVarPosition = getVarFromStack(rhs);

    if(binaryOp == BinaryOperator.Op.Add){


      out.printCode("movq " + lhsVarPosition + "(%rbp), " + "%r10");
      out.printCode("movq " + rhsVarPosition + "(%rbp), " + "%r11");
      out.printCode("addq %r10, %r11");
      out.printCode("movq " + "%r11 , " + destVarPosition + "(%rbp)");

    }else if(binaryOp == BinaryOperator.Op.Sub){
      out.printCode("movq " + lhsVarPosition + "(%rbp), " + "%r10");
      out.printCode("movq " + rhsVarPosition + "(%rbp), " + "%r11");
      out.printCode("subq %r11, %r10");
      out.printCode("movq " + "%r10 , " + destVarPosition + "(%rbp)");
    }else if(binaryOp == BinaryOperator.Op.Mul){
      out.printCode("movq " + lhsVarPosition + "(%rbp), " + "%r10");
      out.printCode("movq " + rhsVarPosition + "(%rbp), " + "%r11");
      out.printCode("imulq %r11, %r10");
      out.printCode("movq " + "%r10 , " + destVarPosition + "(%rbp)");
    }else if(binaryOp == BinaryOperator.Op.Div){
      out.printCode("movq " + lhsVarPosition + "(%rbp), " + "%rax");
      out.printCode("cqto");
      out.printCode("idivq " + rhsVarPosition + "(%rbp)");
      out.printCode("movq %rax, " + destVarPosition + "(%rbp)");
    }

  }

  public void visit(CompareInst i) {
    LocalVar destVar = i.getDst();
    LocalVar lhs = i.getLeftOperand();
    LocalVar rhs = i.getRightOperand();
    CompareInst.Predicate predicate = i.getPredicate();

    int destVarPosition = getVarFromStack(destVar);
    int lhsVarPosition = getVarFromStack(lhs);
    int rhsVarPosition = getVarFromStack(rhs);

    out.printCode("movq $0, %rax");
    out.printCode("movq $1, %r10");
    out.printCode("movq " + lhsVarPosition + "(%rbp), " + "%r11");
    out.printCode("cmp " + rhsVarPosition + "(%rbp), " + "%r11");

    if(predicate == CompareInst.Predicate.GE){
      out.printCode("cmovge %r10, %rax");
    }else if(predicate == CompareInst.Predicate.GT){
      out.printCode("cmovg %r10, %rax");
    }else if(predicate == CompareInst.Predicate.LE){
      out.printCode("cmovle %r10, %rax");
    }else if(predicate == CompareInst.Predicate.LT){
      out.printCode("cmovl %r10, %rax");
    }else if(predicate == CompareInst.Predicate.EQ){
      out.printCode("cmove %r10, %rax");
    }else if(predicate == CompareInst.Predicate.NE){
      out.printCode("cmovne %r10, %rax");
    }

    out.printCode("movq %rax, " + destVarPosition + "(%rbp)");
  }

  // error -8
  public void visit(CopyInst i) {
    Value src = i.getSrcValue();
    LocalVar dest = i.getDstVar();
    int destVarPosition = getVarFromStack(dest);
    if(src instanceof IntegerConstant){
      long intVal = ((IntegerConstant) src).getValue();
      out.printCode("movq $" + intVal + ", %r10");
    }else if(src instanceof BooleanConstant){
      boolean boolVal = ((BooleanConstant) src).getValue();
      if(boolVal){
        out.printCode("movq $1, %r10");
      }else{
        out.printCode("movq $0, %r10");
      }
    }else if(src instanceof LocalVar){
      int srcVarPosition = getVarFromStack((LocalVar) src);
      out.printCode("movq " + srcVarPosition + "(%rbp), %r10");
    }
    out.printCode("movq %r10, " + destVarPosition + "(%rbp)");
  }


  public void visit(JumpInst i) {
    String jmp = labels.get(i.getNext(1));
    int stackPosition = getVarFromStack(i.getPredicate());
    out.printCode("movq " + stackPosition + "(%rbp), %r11");
    out.printCode("cmp $1, %r11");
    out.printCode("je " + jmp);
  }

  public void visit(LoadInst i) {
    AddressVar src = i.getSrcAddress();
    LocalVar dest = i.getDst();

    int srcPosition = getVarFromStack(src);
    int dstPosition = getVarFromStack(dest);

    out.printCode("movq " + srcPosition + "(%rbp), %r10");
    out.printCode("movq 0(%r10), %r11");
    out.printCode("movq %r11, " + dstPosition + "(%rbp)");
  }

  public void visit(NopInst i) {
    return;
  }

  public void visit(StoreInst i) {
    LocalVar src = i.getSrcValue();
    AddressVar dest = i.getDestAddress();

    int srcPosition = getVarFromStack(src);
    int destPosition = getVarFromStack(dest);

    out.printCode("movq " + destPosition + "(%rbp), %r10");
    out.printCode("movq " + srcPosition + "(%rbp), %r11");
    out.printCode("movq %r11, 0(%r10)");
  }

  public void visit(ReturnInst i) {
    LocalVar ret = i.getReturnValue();
    int position = getVarFromStack(ret);
    if(ret != null){
      out.printCode("movq " + position + "(%rbp), %rax");
    }
    out.printCode("leave");
    out.printCode("ret");
  }

  public void visit(CallInst i) {
    String name =i.getCallee().getName();
    List<LocalVar> params = i.getParams();
    putParamsInSpecificRegisters(params);
    if(params.size() > 6){
      for(int j = params.size() - 1; j > 5; j--){
        int position = getVarFromStack(params.get(j));

        out.printCode("movq " + position + "(%rbp), %r10");
        out.printCode("movq %r10 ," + totalVarNum *(-8) + "(%rbp)");
        //numLocalVar++;
      }
    }
    out.printCode("call " + name);
    LocalVar dest = i.getDst();
    if(dest == null){

    }else{
      int pos = getVarFromStack(dest);
      out.printCode("movq %rax, " + pos + "(%rbp)");
    }
  }

  private void putParamsInSpecificRegisters(List<LocalVar> params){
    for(int i = 0; i < params.size(); i++){
      if(i == 6) break;
      int position = getVarFromStack(params.get(i));
      if(i == 0){
        out.printCode("movq " + position + "(%rbp), %rdi");
      }else if(i == 1){
        out.printCode("movq " + position + "(%rbp), %rsi");
      }else if(i == 2){
        out.printCode("movq " + position + "(%rbp), %rdx");
      }else if(i == 3){
        out.printCode("movq " + position + "(%rbp), %rcx");
      }else if(i == 4){
        out.printCode("movq " + position + "(%rbp), %r8");
      }else{
        out.printCode("movq " + position + "(%rbp), %r9");
      }
    }
  }

  public void visit(UnaryNotInst i) {
    LocalVar dst = i.getDst();
    LocalVar inner = i.getInner();

    int dstPosition = getStackPosition(dst)*(-8);
    int innerPosition = getStackPosition(inner)*(-8);

    out.printCode("movq " + innerPosition + "(%rbp), %r10");
    out.printCode("movq $1, %r11");
    out.printCode("not %r10");
    out.printCode("movq %r10, " + dstPosition + "(%rbp)");

  }
}
