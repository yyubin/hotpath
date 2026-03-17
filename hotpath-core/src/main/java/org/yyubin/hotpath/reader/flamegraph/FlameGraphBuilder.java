package org.yyubin.hotpath.reader.flamegraph;

import org.yyubin.hotpath.model.flamegraph.FlameNode;
import org.yyubin.hotpath.reader.flamegraph.CollapsedStacksReader.StackEntry;

import java.util.List;

/**
 * List&lt;StackEntry&gt;를 받아 FlameNode 루트 트리를 구성한다.
 *
 * <p>알고리즘:
 * <ol>
 *   <li>루트 노드("root")를 생성하고 전체 샘플 합을 total로 설정한다.</li>
 *   <li>각 StackEntry의 프레임을 루트부터 순서대로 순회하며 자식 노드를 탐색 또는 생성한다.</li>
 *   <li>방문하는 모든 노드의 total을 해당 entry의 샘플 수만큼 누적한다.</li>
 *   <li>마지막 프레임(리프) 노드에만 self를 누적한다.</li>
 * </ol>
 */
public class FlameGraphBuilder {

    private FlameGraphBuilder() {}

    /**
     * StackEntry 목록으로 FlameNode 트리를 구성해 루트 노드를 반환한다.
     *
     * @param entries CollapsedStacksReader가 반환한 파싱 결과
     * @return 루트 FlameNode
     */
    public static FlameNode build(List<StackEntry> entries) {
        FlameNode root = new FlameNode("root");

        for (StackEntry entry : entries) {
            long samples = entry.samples();
            root.addTotal(samples);

            FlameNode current = root;
            List<String> frames = entry.frames();

            for (int i = 0; i < frames.size(); i++) {
                current = current.findOrCreateChild(frames.get(i));
                current.addTotal(samples);

                if (i == frames.size() - 1) {
                    current.addSelf(samples);
                }
            }
        }

        return root;
    }
}
