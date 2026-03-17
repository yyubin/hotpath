package org.yyubin.hotpath.model.flamegraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FlameNode {

    private final String name;
    private long self;
    private long total;
    private final List<FlameNode> children = new ArrayList<>();

    public FlameNode(String name) {
        this.name = name;
    }

    public String getName()             { return name; }
    public long getSelf()               { return self; }
    public long getTotal()              { return total; }
    public List<FlameNode> getChildren(){ return Collections.unmodifiableList(children); }

    public void addSelf(long count)     { this.self  += count; }
    public void addTotal(long count)    { this.total += count; }

    /** 이름으로 자식 노드를 찾아 반환한다. 없으면 새로 만들어 추가 후 반환. */
    public FlameNode findOrCreateChild(String childName) {
        for (FlameNode child : children) {
            if (child.name.equals(childName)) return child;
        }
        FlameNode newChild = new FlameNode(childName);
        children.add(newChild);
        return newChild;
    }
}
