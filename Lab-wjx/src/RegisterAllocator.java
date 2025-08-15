import org.bytedeco.llvm.LLVM.*;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.bytedeco.llvm.global.LLVM.*;

public class RegisterAllocator {
    private FileWriter fileWriter;

    private final LLVMValueRef func;
    private final Map<String, Integer> varToStack = new HashMap<>(); // 变量名 -> 栈偏移量
    private final Map<String, String> varToReg = new HashMap<>(); // 变量名 -> 寄存器
    private final Map<String, String> regToVar = new HashMap<>(); // 寄存器 -> 变量名

    // 记录变量处在栈中还是寄存器中
    private final Map<String, Boolean> varInStack = new HashMap<>(); // 变量名 -> 是否在栈中
    private final Set<String> usedRegs = new HashSet<>(); // 已使用的寄存器
    private final Set<String> availableRegs = new HashSet<>(); // 可用的寄存器
    private final Set<String> vars = new HashSet<>(); // 已分配的变量
    private final Set<String> regs = new HashSet<>(); // 寄存器
    private int offset = 0; // 偏移量

    // 使用线性扫描寄存器分配
    private final Map<String, Interval> varToInterval = new HashMap<>(); // 变量名 -> 生存区间
    private final Map<String, Interval> active = new HashMap<>(); // 当前占用寄存器的变量的生存区间
    int currentInstIndex = 0; // 当前指令索引

    private final Set<String> tempReg = new HashSet<>(); // 临时寄存器
    private final Set<String> usedTempRegs = new HashSet<>(); // 已使用的临时寄存器
    private final Set<String> availableTempRegs = new HashSet<>(); // 可用的临时寄存器

    private final List<LLVMBasicBlockRef> blocks = new ArrayList<>(); // 基本块列表
    private Map<LLVMBasicBlockRef, Set<LLVMBasicBlockRef>> predecessors = new HashMap<>(); // 前驱基本块列表, 基本块 -> 前驱基本块列表
    private Map<LLVMBasicBlockRef, Integer> blockNumbers = new HashMap<>(); // 基本块编号, 基本块 -> 编号

    public Map<LLVMBasicBlockRef, Integer> getBlockNumbers() {
        return blockNumbers;
    }


    public static class Interval {
        public final String var;
        public final int start;
        public final int end;
        public final int startBlockNumber;
        public final String startBlockName;
        public final int endBlockNumber;
        public final String endBlockName;

        public Interval(String var, int start, int end, int startBlockNumber, String startBlockName, int endBlockNumber, String endBlockName) {
            this.var = var;
            this.start = start;
            this.end = end;
            this.startBlockNumber = startBlockNumber;
            this.startBlockName = startBlockName;
            this.endBlockNumber = endBlockNumber;
            this.endBlockName = endBlockName;

        }
    }

    public Interval computeInterval(String var) { // 计算变量的生存区间
        int start = 0; // 首次定义
        int end = 0; // 最后一次使用
        int startBlockNumber = 0; // 首次定义所在的基本块编号
        LLVMBasicBlockRef startBlock = null; // 首次定义所在的基本块
        int endBlockNumber = 0; // 最后一次使用所在的基本块编号
        LLVMBasicBlockRef endBlock = null; // 最后一次使用所在的基本块
        for (LLVMBasicBlockRef bb = LLVMGetFirstBasicBlock(func); bb != null; bb = LLVMGetNextBasicBlock(bb)) {
            for (LLVMValueRef inst = LLVMGetFirstInstruction(bb); inst != null; inst = LLVMGetNextInstruction(inst)) {
                // alloc,load,icmp,zext和运算会定义变量
                if (start == 0 && startBlock == null) {
                    String varName = LLVMGetValueName(inst).getString();
                    if (varName.equals(var)) {
                        start = currentInstIndex;
                        startBlock = bb;
                        startBlockNumber = blockNumbers.get(bb);
                    }
                }
                // store,load,icmp,zext,br, ret和运算会使用变量
                if (true) {
                    // 检查所有的操作数，如果有一个操作数是var，则更新end
                    int numOperands = LLVMGetNumOperands(inst);
                    for (int i = 0; i < numOperands; i++) {
                        LLVMValueRef operand = LLVMGetOperand(inst, i);
                        String operandName = LLVMGetValueName(operand).getString();
                        if (var.equals("x")) {
                            int a = 1;
                        }
                        if (operandName.equals(var) && LLVMIsAGlobalVariable(operand) == null) {
                            if (endBlock == null || endBlock == bb) {
                                end = currentInstIndex;
                                endBlockNumber = blockNumbers.get(bb);
                                endBlock = bb;
                                break;
                            } else if (endBlockNumber < blockNumbers.get(bb)) {
                                end = currentInstIndex;
                                endBlock = bb;
                                endBlockNumber = blockNumbers.get(bb);
                            } else if (endBlockNumber == blockNumbers.get(bb)) {
                                end = Integer.MAX_VALUE; // 视为该块结束时也不能释放
                            }

                        }
                    }
                }
                currentInstIndex++;
            }
        }
        currentInstIndex = 0; // 重置指令索引
        return new Interval(var, start, end, startBlockNumber, LLVMGetBasicBlockName(startBlock).getString(), endBlockNumber, LLVMGetBasicBlockName(endBlock).getString());
    }

    // 递归分析基本块前驱
    public static Map<LLVMBasicBlockRef, Set<LLVMBasicBlockRef>> computeAllPredecessors(LLVMValueRef function) {
        // Step 1: Compute direct predecessors
        Map<LLVMBasicBlockRef, List<LLVMBasicBlockRef>> directPredecessors = computePredecessors(function);

        // Step 2: Compute all predecessors using DFS
        Map<LLVMBasicBlockRef, Set<LLVMBasicBlockRef>> allPredecessors = new HashMap<>();
        for (LLVMBasicBlockRef block : directPredecessors.keySet()) {
            Set<LLVMBasicBlockRef> visited = new HashSet<>();
            computeIndirectPredecessors(block, directPredecessors, visited);
            allPredecessors.put(block, visited);
        }

        return allPredecessors;
    }

    public static Map<LLVMBasicBlockRef, List<LLVMBasicBlockRef>> computePredecessors(LLVMValueRef function) {
        Map<LLVMBasicBlockRef, List<LLVMBasicBlockRef>> predecessors = new HashMap<>();

        // 遍历函数内的所有基本块
        for (LLVMBasicBlockRef block = LLVMGetFirstBasicBlock(function); block != null; block = LLVMGetNextBasicBlock(block)) {
            // 初始化当前基本块的前驱列表
            predecessors.putIfAbsent(block, new ArrayList<>());

            // 遍历当前基本块中的所有指令，查找跳转指令
            for (LLVMValueRef inst = LLVMGetFirstInstruction(block); inst != null; inst = LLVMGetNextInstruction(inst)) {
                if (LLVMIsABranchInst(inst) != null || LLVMIsASwitchInst(inst) != null) {
                    // 遍历跳转指令的所有目标基本块
                    int numSuccessors = LLVMGetNumSuccessors(inst);
                    for (int i = 0; i < numSuccessors; i++) {
                        LLVMBasicBlockRef successor = LLVMGetSuccessor(inst, i);
                        // 将当前基本块添加到目标基本块的前驱列表中
                        predecessors.putIfAbsent(successor, new ArrayList<>());
                        predecessors.get(successor).add(block);
                    }
                }
            }
        }

        return predecessors;
    }


    private static void computeIndirectPredecessors(
            LLVMBasicBlockRef block,
            Map<LLVMBasicBlockRef, List<LLVMBasicBlockRef>> directPredecessors,
            Set<LLVMBasicBlockRef> visited) {
        if (visited.contains(block)) {
            return;
        }
        visited.add(block);
        List<LLVMBasicBlockRef> directPreds = directPredecessors.getOrDefault(block, new ArrayList<>());
        for (LLVMBasicBlockRef pred : directPreds) {
            computeIndirectPredecessors(pred, directPredecessors, visited);
        }
    }

    public RegisterAllocator(LLVMValueRef func, FileWriter fileWriter) {
        this.func = func;
        this.fileWriter = fileWriter;
        for (int i = 8; i < 30; i++) {
            regs.add("x" + i);
            availableRegs.add("x" + i);
        }
        for (int i = 28; i < 32; i++) {
            tempReg.add("x" + i);
            availableTempRegs.add("x" + i);
        }
        // 将基础块添加到列表中
        for (LLVMBasicBlockRef bb = LLVMGetFirstBasicBlock(func); bb != null; bb = LLVMGetNextBasicBlock(bb)) {
            blocks.add(bb);
        }

        SCCGraph scc = new SCCGraph(func);
        scc.computeSCCs();

//        //  打印SCCList
//        for (List<BlockInfo> sccList : scc.sccList) {
//            System.out.print("SCC: ");
//            for (BlockInfo bb : sccList) {
//                String blockName = LLVMGetBasicBlockName(bb.block).getString();
//                System.out.print(blockName + " ");
//            }
//            System.out.println();
//        }


        TopoSorter topo = new TopoSorter(scc.sccList);
        List<BlockInfo> orderedBlocks = topo.topologicalOrder();

        // 分配block编号,同一个scc分配统一编号
        int id = 0;
        for (BlockInfo bb : orderedBlocks) {
            this.blockNumbers.put(bb.block, bb.sccId);
        }

//        // 打印所有的基本块名称和编号
//        for (Map.Entry<LLVMBasicBlockRef, Integer> entry : blockNumbers.entrySet()) {
//            LLVMBasicBlockRef bb = entry.getKey();
//            String blockName = LLVMGetBasicBlockName(bb).getString();
//            int blockNumber = entry.getValue();
//            System.out.println("Block Name: " + blockName + ", Block Number: " + blockNumber);
//        }




        // 遍历指令，计算区间（活跃变量分析）
        for (LLVMBasicBlockRef bb = LLVMGetFirstBasicBlock(func); bb != null; bb = LLVMGetNextBasicBlock(bb)) {
            for (LLVMValueRef inst = LLVMGetFirstInstruction(bb); inst != null; inst = LLVMGetNextInstruction(inst)) {
                // alloc,load,zext,icmp和运算会产生新的变量
                if (LLVMIsAAllocaInst(inst) != null || LLVMIsABinaryOperator(inst) != null || LLVMIsALoadInst(inst) != null || LLVMIsAZExtInst(inst) != null || LLVMIsAICmpInst(inst) != null) {
                    String varName = LLVMGetValueName(inst).getString();
                    if (!varName.isEmpty()) {
                        vars.add(varName);
                        Interval interval = computeInterval(varName);
                        varToInterval.put(varName, interval);
                    }
                }
            }
        }
        int a = 1;
    }

    public int getMaxSize() {
        // 计算所需的栈大小, 用来prologue
        return 128;
    }

    public void allocate(String name) {
        if (varToReg.containsKey(name)) {
            return; // 已经分配过了
        }

        LSR(name); // 线性扫描寄存器分配
    }

    private void LSR(String name) {
        Interval interval = varToInterval.get(name);
        expireOldIntervals(interval);
        if (availableRegs.isEmpty()) {
            spillAtInterval(interval);
        } else { // 分配寄存器
            String reg = availableRegs.iterator().next();
            availableRegs.remove(reg);
            usedRegs.add(reg);
            varToReg.put(name, reg);
            regToVar.put(reg, name);
            varInStack.put(name, false);
            active.put(name, interval);
        }
    }

    private void spillAtInterval(Interval interval) { // 辅助函数: 处理溢出
        // 取出active中end最大的一个区间
        Interval spill = null;
        int end = 0;
        int endBlockNum = 0;
        for (Map.Entry<String, Interval> entry : active.entrySet()) {
            if ((entry.getValue().end > end && Objects.equals(entry.getValue().endBlockName, interval.endBlockName)) || (entry.getValue().endBlockNumber > endBlockNum)) {
                end = entry.getValue().end;
                endBlockNum = entry.getValue().endBlockNumber;
                spill = entry.getValue();
            }
        }
        if (spill != null && ((spill.end > interval.end && Objects.equals(spill.endBlockName, interval.endBlockName)) || (spill.endBlockNumber > interval.endBlockNumber))) {
            // 让 interval 占用 spill 的寄存器, spill 被溢出到内存
            String spillVar = spill.var;
            String reg = varToReg.get(spillVar);
            String newVar = interval.var;
            if (reg != null) {
                // 分配新的栈空间
                try {
                    fileWriter.write("    sw " + reg + ", " + offset + "(sp)\n");
                    varToStack.put(spillVar, offset);
                    offset += 4; // 每个变量占用4字节
                } catch (IOException e) {
                    e.printStackTrace();
                }
                varInStack.put(spillVar, true);
                varToReg.remove(spillVar);
                regToVar.remove(reg);
                varToReg.put(newVar, reg);
                regToVar.put(reg, newVar);
                active.remove(spillVar);
                active.put(newVar, interval);
            }
        }else {
            // 直接溢出, 不占用寄存器
            String newVar = interval.var;
            varToStack.put(newVar, offset);
            varInStack.put(newVar, true);
            offset += 4; // 每个变量占用4字节
        }
    }

    private void expireOldIntervals(Interval interval) { // 辅助函数: 释放已结束的区间
        // 按 endpoint 从小到大遍历active
        Iterator<Map.Entry<String, Interval>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Interval> entry = iterator.next();
            Interval activeInterval = entry.getValue();
            if ((Objects.equals(activeInterval.endBlockName, interval.startBlockName) && activeInterval.end < interval.start) || activeInterval.endBlockNumber < interval.startBlockNumber) { // 变量生存区间已结束
                // 将activeInterval从寄存器释放
                String var = activeInterval.var;
                String reg = varToReg.get(var);
                if (reg != null) {
                    availableRegs.add(reg);
                    usedRegs.remove(reg);
                    varToReg.remove(var);
                    regToVar.remove(reg);
                }
                iterator.remove(); // 使用迭代器安全地移除元素
            }
        }

    }

    public void freeAllReg() throws IOException {
        Iterator<String> iterator = usedRegs.iterator();
        while (iterator.hasNext()) {
            String reg = iterator.next();
            String varName = regToVar.get(reg);
            if (varName == null) {
                iterator.remove(); // Safely remove the element
                availableRegs.add(reg);
                continue; // 寄存器没有对应的变量
            }
            int offset = getVarInStack(varName);
            fileWriter.write("    sw " + reg + ", " + offset + "(sp)\n");
            varInStack.put(varName, true);
            iterator.remove(); // Safely remove the element
            availableRegs.add(reg);
        }
    }

    public String stackToReg(String varName) throws IOException {
        LSR(varName);
        String reg = varToReg.get(varName);
        return reg;
    }

    public String getVarInReg(String name) throws IOException { // 将变量提取到寄存器中，返回寄存器的名称
        if (varToReg.containsKey(name)) {
            return varToReg.get(name);
        }
//        if (varToStack.containsKey(name)) {
//            return stackToReg(name);
//        }
        return null;
    }

    // 直接分配临时变量到寄存器
    public String allocateTempVar() throws IOException {
        String reg = this.availableTempRegs.iterator().next();
        availableTempRegs.remove(reg);
        usedTempRegs.add(reg);
        return reg;
    }

    // 回收临时变量寄存器
    public void freeTempVarReg(String reg) throws IOException {
        if (usedTempRegs.contains(reg)) {
            usedTempRegs.remove(reg);
            availableTempRegs.add(reg);
        }
    }

    public Integer getVarInStack(String name) { // 获取变量偏移量
        if (varToStack.containsKey(name)) {
            return varToStack.get(name);
        }
        return null;
    }

    // 分析基本块执行前后关系
    public Map<LLVMBasicBlockRef, Integer> analyzeExecutionOrder(List<LLVMBasicBlockRef> blocks, Map<LLVMBasicBlockRef, Set<LLVMBasicBlockRef>> predecessors) {
        // 构建图
        Map<LLVMBasicBlockRef, Set<LLVMBasicBlockRef>> graph = new HashMap<>();
        for (LLVMBasicBlockRef block : blocks) {
            graph.putIfAbsent(block, new HashSet<>());
        }
        for (Map.Entry<LLVMBasicBlockRef, Set<LLVMBasicBlockRef>> entry : predecessors.entrySet()) {
            LLVMBasicBlockRef block = entry.getKey();
            for (LLVMBasicBlockRef pred : entry.getValue()) {
                graph.get(pred).add(block);
            }
        }

        // 使用 Tarjan 算法找 SCC
        AtomicInteger index = new AtomicInteger(0);
        Map<LLVMBasicBlockRef, Integer> indices = new HashMap<>();
        Map<LLVMBasicBlockRef, Integer> low = new HashMap<>();
        Set<LLVMBasicBlockRef> onStack = new HashSet<>();
        Deque<LLVMBasicBlockRef> stack = new ArrayDeque<>();
        List<List<LLVMBasicBlockRef>> sccs = new ArrayList<>();

        for (LLVMBasicBlockRef block : blocks) {
            if (!indices.containsKey(block)) {
                strongConnect(block, graph, indices, low, onStack, stack, sccs, index);
            }
        }

        // 构建 SCC 的 DAG
        Map<Integer, Set<Integer>> sccGraph = new HashMap<>();
        Map<LLVMBasicBlockRef, Integer> sccMap = new HashMap<>();
        for (int i = 0; i < sccs.size(); i++) {
            for (LLVMBasicBlockRef block : sccs.get(i)) {
                sccMap.put(block, i);
            }
        }
        Map<Integer, Integer> inDegree = new HashMap<>();
        for (int i = 0; i < sccs.size(); i++) {
            sccGraph.putIfAbsent(i, new HashSet<>());
            inDegree.putIfAbsent(i, 0);
        }
        for (LLVMBasicBlockRef u : graph.keySet()) {
            for (LLVMBasicBlockRef v : graph.get(u)) {
                int sccU = sccMap.get(u);
                int sccV = sccMap.get(v);
                if (sccU != sccV && !sccGraph.get(sccU).contains(sccV)) {
                    sccGraph.get(sccU).add(sccV);
                    inDegree.put(sccV, inDegree.get(sccV) + 1);
                }
            }
        }

        // 拓扑排序
        Queue<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < sccs.size(); i++) {
            if (inDegree.get(i) == 0) {
                queue.add(i);
            }
        }
        List<Integer> topoOrder = new ArrayList<>();
        while (!queue.isEmpty()) {
            int node = queue.poll();
            topoOrder.add(node);
            for (int neighbor : sccGraph.get(node)) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }

        // 分配编号
        Map<LLVMBasicBlockRef, Integer> blockNumbers = new HashMap<>();
        int number = 1;
        for (int sccId : topoOrder) {
            for (LLVMBasicBlockRef block : sccs.get(sccId)) {
                blockNumbers.put(block, number);
            }
            number++;
        }

        return blockNumbers;
    }

    // 修改后的strongConnect方法
    private void strongConnect(
            LLVMBasicBlockRef v,
            Map<LLVMBasicBlockRef, Set<LLVMBasicBlockRef>> graph,
            Map<LLVMBasicBlockRef, Integer> indices,
            Map<LLVMBasicBlockRef, Integer> low,
            Set<LLVMBasicBlockRef> onStack,
            Deque<LLVMBasicBlockRef> stack,
            List<List<LLVMBasicBlockRef>> sccs,
            AtomicInteger index
    ) {
        indices.put(v, index.get());
        low.put(v, index.get());
        index.incrementAndGet();
        stack.push(v);
        onStack.add(v);

        for (LLVMBasicBlockRef w : graph.get(v)) {
            if (!indices.containsKey(w)) {
                strongConnect(w, graph, indices, low, onStack, stack, sccs, index);
                low.put(v, Math.min(low.get(v), low.get(w)));
            } else if (onStack.contains(w)) {
                low.put(v, Math.min(low.get(v), indices.get(w)));
            }
        }

        if (low.get(v).equals(indices.get(v))) {
            List<LLVMBasicBlockRef> scc = new ArrayList<>();
            LLVMBasicBlockRef w;
            do {
                w = stack.pop();
                onStack.remove(w);
                scc.add(w);
            } while (!w.equals(v));
            sccs.add(scc);
        }
    }

    class BlockInfo {
        public int sccId;
        LLVMBasicBlockRef block;
        int index = -1;
        int lowlink = -1;
        boolean onStack = false;
        List<BlockInfo> successors = new ArrayList<>();

        public BlockInfo(LLVMBasicBlockRef block) {
            this.block = block;
        }
    }

    class SCCGraph {
        List<BlockInfo> allBlocks = new ArrayList<>();
        Map<LLVMBasicBlockRef, BlockInfo> blockMap = new HashMap<>();
        List<List<BlockInfo>> sccList = new ArrayList<>();

        int index = 0;
        Deque<BlockInfo> stack = new ArrayDeque<>();

        public SCCGraph(LLVMValueRef function) {
            // Build block info and successors
            for (LLVMBasicBlockRef bb = LLVMGetFirstBasicBlock(function); bb != null; bb = LLVMGetNextBasicBlock(bb)) {
                BlockInfo bi = new BlockInfo(bb);
                blockMap.put(bb, bi);
                allBlocks.add(bi);
            }
            for (LLVMBasicBlockRef bb = LLVMGetFirstBasicBlock(function); bb != null; bb = LLVMGetNextBasicBlock(bb)) {
                BlockInfo bi = blockMap.get(bb);
                for (LLVMValueRef inst = LLVMGetFirstInstruction(bb); inst != null; inst = LLVMGetNextInstruction(inst)) {
                    if (LLVMIsABranchInst(inst) != null || LLVMIsASwitchInst(inst) != null) {
                        int numSuccessors = LLVMGetNumSuccessors(inst);
                        for (int i = 0; i < numSuccessors; i++) {
                            LLVMBasicBlockRef succ = LLVMGetSuccessor(inst, i);
                            bi.successors.add(blockMap.get(succ));
                        }
                    }
                }
            }
        }

        public void computeSCCs() {
            for (BlockInfo bi : allBlocks) {
                if (bi.index == -1) {
                    strongConnect(bi);
                }
            }
        }

        private void strongConnect(BlockInfo v) {
            v.index = index;
            v.lowlink = index;
            index++;
            stack.push(v);
            v.onStack = true;

            for (BlockInfo w : v.successors) {
                if (w.index == -1) {
                    strongConnect(w);
                    v.lowlink = Math.min(v.lowlink, w.lowlink);
                } else if (w.onStack) {
                    v.lowlink = Math.min(v.lowlink, w.index);
                }
            }

            if (v.lowlink == v.index) {
                List<BlockInfo> scc = new ArrayList<>();
                BlockInfo w;
                do {
                    w = stack.pop();
                    w.onStack = false;
                    scc.add(w);
                } while (w != v);
                sccList.add(scc);
            }
        }
    }

    class TopoSorter {
        List<List<BlockInfo>> sccList;
        Map<BlockInfo, Integer> sccIdMap = new HashMap<>();
        List<Set<Integer>> dag = new ArrayList<>();  // compressed DAG by SCC
        List<Integer> inDegree = new ArrayList<>();

        public TopoSorter(List<List<BlockInfo>> sccList) {
            this.sccList = sccList;
            for (int i = 0; i < sccList.size(); i++) {
                for (BlockInfo bb : sccList.get(i)) {
                    sccIdMap.put(bb, i);
                }
            }

            // Build DAG between SCCs
            for (int i = 0; i < sccList.size(); i++) {
                dag.add(new HashSet<>());
                inDegree.add(0);
            }

            for (int i = 0; i < sccList.size(); i++) {
                for (BlockInfo bi : sccList.get(i)) {
                    for (BlockInfo succ : bi.successors) {
                        int from = i;
                        int to = sccIdMap.get(succ);
                        if (from != to && dag.get(from).add(to)) {
                            inDegree.set(to, inDegree.get(to) + 1);
                        }
                    }
                }
            }
        }

        public List<BlockInfo> topologicalOrder() {
            Queue<Integer> q = new ArrayDeque<>();
            for (int i = 0; i < sccList.size(); i++) {
                if (inDegree.get(i) == 0) {
                    q.add(i);
                }
            }

            List<BlockInfo> result = new ArrayList<>();
            int topoId = 0; // 拓扑顺序编号
            while (!q.isEmpty()) {
                int sccId = q.poll();
                List<BlockInfo> blockGroup = sccList.get(sccId);
                for (BlockInfo bb : blockGroup) {
                    bb.sccId = topoId;  // 分配统一编号
                    result.add(bb);
                }
                for (int next : dag.get(sccId)) {
                    inDegree.set(next, inDegree.get(next) - 1);
                    if (inDegree.get(next) == 0) {
                        q.add(next);
                    }
                }
                topoId++; // 每个 SCC 一个编号
            }

            return result;
        }
    }




}


