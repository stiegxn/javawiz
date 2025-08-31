<template>
  <div v-if="streamVizInfo" id="io-viz">
    <NavigationBarWithSettings :pane-kind="STREAMVIZ" />
    <div v-if="streamVizInfo.marbles.length > 0" id="stream-viz">
      <div class="controls">
        <button id="prevBtn" class="myButton" @click="stepBack">
          &laquo;
        </button>
        <button id="nextBtn" class="myButton" @click="stepForwards">
          &raquo;
        </button>
      </div>
      <svg id="stream-viz-svg" width="2000" height="1500" />
    </div>
    <div v-else>
      <div>
        Execute some stream operations to enable this view.
      </div>
    </div>
  </div>
</template>

<script setup lang = 'ts'>
import {computed, defineComponent, nextTick, ref, watch} from 'vue'
import NavigationBarWithSettings from '@/components/NavigationBarWithSettings.vue'
import {STREAMVIZ} from '@/store/PaneVisibilityStore'
import { useGeneralStore } from '@/store/GeneralStore'
import * as d3 from 'd3'

defineComponent({
  name: 'TheStreamViz',
  components: { NavigationBarWithSettings }
})
const generalStore = useGeneralStore()
const streamVizInfo = computed(() => generalStore.currentTraceData?.streamVizInfo)
const allNodes = computed(() => streamVizInfo.value.marbles)
const allLinks = computed(() => streamVizInfo.value.links)
const operationLines = computed(() => streamVizInfo.value.operationLines)
const RADIUS = 15;
const currentStep = ref(0);
let svg: any;
let container: d3.Selection<SVGGElement, unknown, any, undefined>;
let linkGroup: d3.Selection<SVGGElement, unknown, any, undefined>;
let nodeGroup: d3.Selection<SVGGElement, unknown, any, undefined>;
let lastNodeX;

// Hilfsfunktion: Für jede ID nur den neuesten Knoten (max step <= currentStep) auswählen
function getVisibleNodesAtStep(nodes: any, step: number) {
  const grouped = d3.group(
    nodes.filter((n: any) => n.id <= step),
    (d: any) => d.elemId
  );
  return Array.from(grouped, ([_, group]) =>
    group.reduce((maxNode: any, current: any) => current.id > maxNode.id ? current : maxNode)
  );
}

function edgePoint(src: any, tgt: any) {
  const dx = tgt.x - src.x;
  const dy = tgt.y - src.y;
  const dist = Math.sqrt(dx*dx + dy*dy);
  if(dist === 0) return { x: src.x, y: src.y };
  const offsetX = (dx / dist) * RADIUS;
  const offsetY = (dy / dist) * RADIUS;
  return { x: src.x + offsetX, y: src.y + offsetY };
}


function render() {

  const visibleNodes = getVisibleNodesAtStep(allNodes.value, currentStep.value);
  const visibleNodeIds = new Set(visibleNodes.map(n => n.elemId));
  const visibleLinks = allLinks.value.filter((l: any) =>
    l.visibleAt <= currentStep.value &&
    visibleNodeIds.has(l.source) &&
    visibleNodeIds.has(l.target)
  );
  const nodes = nodeGroup.selectAll('.node')
    .data(visibleNodes, (d: any) => d.id)
    .join(
      enter => {
        const g = enter.append('g')
          .attr('class', 'node')
          .attr('transform', d => `translate(${d.x},${d.y})`)
          .style('opacity', 0)
        g.each(function(d: any) {
          const group = d3.select(this);

          if (d.direction === 'IN') {
            const size = RADIUS + 5;

            group.append('circle')
              .attr('r', size)
              .attr('stroke', '#303030')
              .attr('stroke-width', 2)
              .attr('fill', 'white');

            const crossSize = size * 0.7;
            group.append('line')
              .attr('x1', -crossSize)
              .attr('y1', -crossSize)
              .attr('x2', crossSize)
              .attr('y2', crossSize)
              .attr('stroke', '#303030')
              .attr('stroke-width', 2)
              .attr('stroke-linecap', 'round');

            group.append('line')
              .attr('x1', -crossSize)
              .attr('y1', crossSize)
              .attr('x2', crossSize)
              .attr('y2', -crossSize)
              .attr('stroke', '#303030')
              .attr('stroke-width', 2)
              .attr('stroke-linecap', 'round');
          } else {
            group.append('circle')
              .attr('r', RADIUS + 5)
              .attr('stroke', '#333')
              .attr('stroke-width', 1.5)
              .attr('fill', d.color);
            g.append('text')
              .text(d => d.label)
              .attr('text-anchor', 'middle')
              .attr('dominant-baseline', 'middle');
          }
        });

        return g;
      },
      update => update,
      exit => exit.transition()
        .duration(300)
        .style('opacity', 0)
        .remove()
    );
  nodes.transition()
    .duration(500)
    .attr('transform', d => `translate(${d.x},${d.y})`)
    .style('opacity', 1);

  nodes.select('circle').transition()
    .duration(500)
    .attr('fill', d => d.color);

  nodes.select('text').text(d => d.direction === 'IN' ? '' : d.label);

  const nodeLinksMap = new Map(visibleNodes.map(node => [node.elemId, node]));
  const links = linkGroup.selectAll('.link')
    .data(visibleLinks, (d: any) => d.source + '-' + d.target)
    .join(
      enter => enter.append('line')
        .attr('class', 'link')
        .style('opacity', 0)
        .attr('x1', (d: any) => {
          const src = nodeLinksMap.get(d.source);
          return src ? src.x : 0;
        })
        .attr('y1', (d: any) => {
          const src = nodeLinksMap.get(d.source);
          return src ? src.y : 0;
        })
        .attr('x2', (d: any) => { // Start the line collapsed at the source
          const src = nodeLinksMap.get(d.source);
          return src ? src.x : 0;
        })
        .attr('y2', (d: any) => { // Start the line collapsed at the source
          const src = nodeLinksMap.get(d.source);
          return src ? src.y : 0;
        }),
      update => update,
      exit => exit
        .transition()
        .duration(300) // Use a slightly faster exit duration
        .style('opacity', 0)
        .remove()
    );

  links.transition()
    .duration(500)
    .style('opacity', 1) // Fades in new links
    .attr('x1', (d: any) => {
      const src = nodeLinksMap.get(d.source);
      const tgt = nodeLinksMap.get(d.target);
      return edgePoint(src, tgt).x;
    })
    .attr('y1', (d: any) => {
      const src = nodeLinksMap.get(d.source);
      const tgt = nodeLinksMap.get(d.target);
      return edgePoint(src, tgt).y;
    })
    .attr('x2', (d: any) => {
      const src = nodeLinksMap.get(d.source);
      const tgt = nodeLinksMap.get(d.target);
      return edgePoint(tgt, src).x;
    })
    .attr('y2', (d: any) => {
      const src = nodeLinksMap.get(d.source);
      const tgt = nodeLinksMap.get(d.target);
      return edgePoint(tgt, src).y;
    });
}

function stepForwards() {
  if (currentStep.value < allNodes.value.reduce((max: any, n: any) => Math.max(max, n.id), 0)) {
    currentStep.value++;
  }
}

function stepBack() {
  if (currentStep.value > 0) {
    currentStep.value--;
  }
}

function zoomed(event: any) {
  container.attr('transform', event.transform);
}

watch(streamVizInfo, () => {
  if (streamVizInfo.value.marbles.length > 0) {
    nextTick(() => {
      svg = d3.select('#stream-viz-svg');
      svg.selectAll('*').remove();

      container = svg.append('g').attr('class', 'container');

      lastNodeX = Math.max(...allNodes.value.map((n: any) => n.x));
      svg.append('defs').append('marker')
        .attr('id', 'arrow')
        .attr('viewBox', '0 -5 10 10')
        .attr('refX', 13)
        .attr('refY', 0)
        .attr('markerWidth', 6)
        .attr('markerHeight', 6)
        .attr('orient', 'auto')
        .append('path')
        .attr('d', 'M0,-5L10,0L0,5')
        .attr('fill', '#999');

      container.selectAll('.opline')
        .data(Object.values({ ...operationLines.value }))
        .join('line')
        .attr('class', 'opline')
        .attr('x1', 0)
        .attr('y1', (d: any) => d.y)
        .attr('x2', lastNodeX + 110)
        .attr('y2', (d: any) => d.y)
        .attr('stroke', '#999')
        .attr('stroke-width', 1.5)
        .attr('stroke-dasharray', '8 4');

      container.selectAll('.oplabel')
        .data(Object.values({ ...operationLines.value }))
        .join('text')
        .attr('x', lastNodeX + 100)
        .attr('y', (d: any) => d.y - 5)
        .attr('text-anchor', 'end')
        .text((d: any) => d.type)
        .attr('fill', '#888')
        .attr('font-size', '11px')
        .attr('style', 'font-size: 1.5rem; user-select: none;');

      linkGroup = container.append('g').attr('class', 'links');
      nodeGroup = container.append('g').attr('class', 'nodes');

      const zoom = d3.zoom()
        .scaleExtent([0.1, 10])
        .on('zoom', zoomed);
      svg.call(zoom);

      render();
    });
  }
});

watch(currentStep, () => {
  render();
});
</script>

<style>
.link {
  fill: none;
  stroke: #999;
  stroke-width: 2px;
  marker-end: url(#arrow);
}
.node circle {
  stroke: #333;
  stroke-width: 1.5px;
  padding: 5px;
}
text {
  font-size: 1.3rem;
  user-select: none;
}
.controls {
  margin: 20px;
  display: flex;
}
.myButton {
  font-size: 2rem;
  border-radius: 50px;
  background-color: lightgrey;
  margin: 10px;
  padding: 5px 25px 5px 25px;
  cursor: pointer;
}
</style>
