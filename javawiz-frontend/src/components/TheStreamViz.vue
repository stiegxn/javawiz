<template>
  <div v-if="streamVizInfo" id="io-viz">
    <NavigationBarWithSettings :pane-kind="STREAMVIZ" />
    <div v-if="streamVizInfo.marbles.length > 0" id="stream-viz">
      <div class="controls">
        <button class="myButton" id="prevBtn" @click="stepBack" >&laquo;</button>
        <button class="myButton" id="nextBtn" @click="stepForwards">&raquo;</button>
      </div>
      <svg id="stream-viz-svg" width="2000" height="1500"></svg>
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
import sanitizer from '@/helpers/sanitizer'
import NavigationBarWithSettings from '@/components/NavigationBarWithSettings.vue'
import {INVIZ, STREAMVIZ} from '@/store/PaneVisibilityStore'
import { useGeneralStore } from '@/store/GeneralStore'
import * as d3 from 'd3'
import * as stream from "node:stream";

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
let svg;
let container;
let linkGroup;
let nodeGroup;
let lastNodeX;

// Hilfsfunktion: Für jede ID nur den neuesten Knoten (max step <= currentStep) auswählen
function getVisibleNodesAtStep(nodes, step) {
  const grouped = d3.group(
      nodes.filter(n => n.id <= step),
      d => d.elemId
  );
  console.log("grouped: ", grouped);
  return Array.from(grouped, ([id, group]) =>
      group.reduce((maxNode, current) => current.id > maxNode.id ? current : maxNode)
  );
}

function edgePoint(src, tgt) {
  const dx = tgt.x - src.x;
  const dy = tgt.y - src.y;
  const dist = Math.sqrt(dx*dx + dy*dy);
  if(dist === 0) return { x: src.x, y: src.y };
  const offsetX = (dx / dist) * RADIUS;
  const offsetY = (dy / dist) * RADIUS;
  return { x: src.x + offsetX, y: src.y + offsetY };
}


function render() {
  console.log("Start render()")
  console.log(svg);

  const visibleNodes = getVisibleNodesAtStep(allNodes.value, currentStep.value);
  console.log(visibleNodes);
  const visibleNodeIds = new Set(visibleNodes.map(n => n.elemId));
  console.log("visibleNodeIds: ", visibleNodeIds);

  console.log("allLinks: ", allLinks.value);
  const visibleLinks = allLinks.value.filter(l =>
      l.visibleAt <= currentStep.value &&
      visibleNodeIds.has(l.source) &&
      visibleNodeIds.has(l.target)
  );
  console.log("links: ", visibleLinks);

  // const nodes = nodeGroup.selectAll(".node")
  //     .data(visibleNodes, d => d.id);
  // console.log(nodes);
  //
  // nodes.transition()
  //     .duration(500)
  //     .attr("transform", d => `translate(${d.x},${d.y})`);
  //
  // nodes.select("circle").transition()
  //     .duration(500)
  //     .attr("fill", d => d.color)
  // ;
  //
  // nodes.select("text").transition()
  //     .duration(500)
  //     .text(d => d.label);
  //
  // const nodesEnter = nodes.enter()
  //     .append("g")
  //     .attr("class", "node")
  //     .attr("transform", d => `translate(${d.x},${d.y})`)
  //     .style("opacity", 0);
  //
  // nodesEnter.append("circle")
  //     .attr("r", RADIUS)
  //     .attr("stroke", "#333")
  //     .attr("stroke-width", 1.5)
  //     .attr("fill", d => d.color)
  //     .attr("r", 20);
  //
  // nodesEnter.append("text")
  //     .text(d => d.label)
  //     .attr("text-anchor", "middle")
  //     .attr("dominant-baseline", "middle");
  //
  // nodesEnter.transition()
  //     .duration(500)
  //     .style("opacity", 1);
  //
  // nodes.exit()
  //     .transition()
  //     .duration(300)
  //     .style("opacity", 0)
  //     .remove();
  console.log("visibleNodes: ", visibleNodes);
  const nodes = nodeGroup.selectAll(".node")
      .data(visibleNodes, d => d.id)
      .join(
          enter => enter.append("g")
                        .attr("class", "node")
                        .attr("transform", d => `translate(${d.x},${d.y})`)
                        .style("opacity", 0)
                        .call(g => g.append("circle")
                                    .attr("r", RADIUS+5)
                                    .attr("stroke", "#333")
                                    .attr("stroke-width", 1.5)
                                    .attr("fill", d => d.color)
                        )
                        .call(g => g.append("text")
                                    .text(d => d.label)
                                    .attr("text-anchor", "middle")
                                    .attr("dominant-baseline", "middle")
                        ),
      update => update,
      exit => exit.transition()
                  .duration(300)
                  .style("opacity", 0)
                  .remove()
  );
  nodes.transition()
      .duration(500)
      .attr("transform", d => `translate(${d.x},${d.y})`)
      .style("opacity", 1);

  nodes.select("circle").transition()
      .duration(500)
      .attr("fill", d => d.color);

  nodes.select("text").text(d => d.label);

  const links = linkGroup.selectAll(".link")
      .data(visibleLinks, d => d.source + "-" + d.target);

  links.transition()
      .duration(500)
      .attr("x1", d => {
        const src = visibleNodes.find(n => n.elemId === d.source);
        const tgt = visibleNodes.find(n => n.elemId === d.target);
        return edgePoint(src, tgt).x;  // Rand am Source Richtung Target
      })
      .attr("y1", d => {
        const src = visibleNodes.find(n => n.elemId === d.source);
        const tgt = visibleNodes.find(n => n.elemId === d.target);
        return edgePoint(src, tgt).y;
      })
      .attr("x2", d => {
        const src = visibleNodes.find(n => n.elemId === d.source);
        const tgt = visibleNodes.find(n => n.elemId === d.target);
        return edgePoint(tgt, src).x;  // Rand am Target Richtung Source
      })
      .attr("y2", d => {
        const src = visibleNodes.find(n => n.elemId === d.source);
        const tgt = visibleNodes.find(n => n.elemId === d.target);
        return edgePoint(tgt, src).y;
      })
      .style("opacity", 1);

  const linksEnter = links.enter()
      .append("line")
      .attr("class", "link")
      .attr("x1", d => {
        const src = visibleNodes.find(n => n.elemId === d.source);
        return src ? src.x : 0;
      })
      .attr("y1", d => {
        const src = visibleNodes.find(n => n.elemId === d.source);
        return src ? src.y : 0;
      })
      .attr("x2", d => {
        const src = visibleNodes.find(n => n.elemId === d.source);
        return src ? src.x : 0;
      })
      .attr("y2", d => {
        const src = visibleNodes.find(n => n.elemId === d.source);
        return src ? src.y : 0;
      })
      .style("opacity", 0)
      .transition()
      .duration(500)
      .attr("x1", d => {
        const src = visibleNodes.find(n => n.elemId === d.source);
        const tgt = visibleNodes.find(n => n.elemId === d.target);
        return edgePoint(src, tgt).x;
      })
      .attr("y1", d => {
        const src = visibleNodes.find(n => n.elemId === d.source);
        const tgt = visibleNodes.find(n => n.elemId === d.target);
        return edgePoint(src, tgt).y;
      })
      .attr("x2", d => {
        const src = visibleNodes.find(n => n.elemId === d.source);
        const tgt = visibleNodes.find(n => n.elemId === d.target);
        return edgePoint(tgt, src).x;
      })
      .attr("y2", d => {
        const src = visibleNodes.find(n => n.elemId === d.source);
        const tgt = visibleNodes.find(n => n.elemId === d.target);
        return edgePoint(tgt, src).y;
      })
      .style("opacity", 1);

  links.exit()
      .transition()
      .style("opacity", 0)
      .remove();
}

function stepForwards() {
  if (currentStep.value < allNodes.value.reduce((max, n) => Math.max(max, n.id), 0)) {
    currentStep.value++;
  }
}

function stepBack() {
  if (currentStep.value > 0) {
    currentStep.value--;
  }
}

function zoomed(event) {
  container.attr("transform", event.transform);
}

watch(streamVizInfo, () => {
  if (streamVizInfo.value.marbles.length > 0) {
    nextTick(() => {
      svg = d3.select("#stream-viz-svg");
      svg.selectAll("*").remove();

      container = svg.append("g").attr("class", "container");

      lastNodeX = Math.max(...allNodes.value.map(n => n.x));
      console.log("lastNodeX: ",lastNodeX);
      console.log(allNodes.value.map(n => n.x));
      console.log(allNodes.value)
      svg.append("defs").append("marker")
          .attr("id", "arrow")
          .attr("viewBox", "0 -5 10 10")
          .attr("refX", 13)
          .attr("refY", 0)
          .attr("markerWidth", 6)
          .attr("markerHeight", 6)
          .attr("orient", "auto")
          .append("path")
          .attr("d", "M0,-5L10,0L0,5")
          .attr("fill", "#999");

      container.selectAll(".opline")
          .data(Object.values({ ...operationLines.value }))
          .join("line")
          .attr("class", "opline")
          .attr("x1", 0)
          .attr("y1", d => d.y)
          .attr("x2", lastNodeX + 50)
          .attr("y2", d => d.y)
          .attr("stroke", "#999")
          .attr("stroke-width", 1.5)
          .attr("stroke-dasharray", "8 4");

      container.selectAll(".oplabel")
          .data(Object.values({ ...operationLines.value }))
          .join("text")
          .attr("x", lastNodeX + 40)
          .attr("y", d => d.y - 5)
          .attr("text-anchor", "end")
          .text(d => d.type)
          .attr("fill", "#888")
          .attr("font-size", "11px")
          .attr("style", "font-size: 1.5rem; user-select: none;");

      linkGroup = container.append("g").attr("class", "links");
      nodeGroup = container.append("g").attr("class", "nodes");

      const zoom = d3.zoom()
          .scaleExtent([0.1, 10])
          .on("zoom", zoomed);
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
