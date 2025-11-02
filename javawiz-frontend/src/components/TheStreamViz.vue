<template>
  <div v-if="streamVizInfo" id="io-viz">
    <NavigationBarWithSettings
        :zoom-reset="zoomAndCenter"
        :pane-kind="STREAMVIZ"
    />
    <div v-if="streamVizInfo.marbles.length > 0" id="stream-viz" style="height: 100%;">
      <div class="controls">
        <button id="prevBtn" class="myButton" @click="stepBack">
          &laquo;
        </button>
        <div @click="editMode = true" style="user-select: none; cursor: pointer;">
          <span v-if="!editMode">{{ currentStep }}</span>
          <span v-else>
            <input
                ref="stepInput"
                class="inputField"
                type="number"
                v-model.number="inputStep"
                min="0"
                :max="allNodes.length"
                @keyup.esc="cancelEdit"
                @keyup.enter="applyStep"
                @blur="applyStep"
                autofocus
            />
          </span>
          <span style="font-weight: bolder; font-size: 1.2rem;">/{{ allNodes.length }}</span>
        </div>
        <button id="nextBtn" class="myButton" @click="stepForwards">
          &raquo;
        </button>
        <div v-if="!isPlaying" class="playControls">
          <button id="resumeBtn" class="playButton" @click="playFrom" v-if="currentStep > 0 && currentStep < allNodes.length">
            <span style="letter-spacing: -0.5rem;">▶▶</span>
          </button>
          <button id="startBtn" class="playButton" @click="playThrough">
            ▶
          </button>
        </div>
        <div v-else class="playControls">
          <button id="pauseBtn" class="stopButton" @click="stopPlaying">
            ❚❚
          </button>
          <button id="stopBtn" class="stopButton" @click="stopAndResetPlaying" style="font-size: 1.4rem;">
            ■
          </button>
        </div>
      </div>
      <svg id="stream-viz-svg" width="100%" height="100%"/>
    </div>
    <div v-else>
      <div>
        Execute some stream operations to enable this view.
      </div>
    </div>
  </div>
</template>

<script setup lang = 'ts'>
import {computed, defineComponent, nextTick, onUnmounted, ref, watch} from 'vue'
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
const heap = computed(() => generalStore.currentTraceData?.processedTraceState?.heapBeforeExecution)
const RADIUS = 15;
const HALFWIDTH = 75;
const WIDTH = 150;
const MARGIN = 10;
const isPlaying = ref(false);
const currentStep = ref(0);
const editMode = ref(false);
const inputStep = ref(0);
const stepInput = ref<HTMLInputElement | null>(null);
const circleFontSize = new Map<number, string>([
    [1, '21px'],
    [2, '21px'],
    [3, '21px'],
    [4, '17px'],
    [5, '14px'],
    [6, '12px'],
    [7, '10px'],
    [8, '9px'],
    [9, '8px'],
    [10, '7px']
  ]);

const miniCircleFontSize = new Map<number, string>([
  [1, '16px'],
  [2, '16px'],
  [3, '14px'],
  [4, '12px'],
  [5, '10px'],
  [6, '8px'],
  [7, '7px'],
  [8, '6px'],
  [9, '5px'],
  [10, '5px']
]);

const miniStringLength = new Map<number, number>([
  [1, 15],
  [2, 7],
  [3, 5],
  [4, 4]
]);

let svg: any;
let container: d3.Selection<SVGGElement, unknown, any, undefined>;
let linkGroup: d3.Selection<SVGGElement, unknown, any, undefined>;
let nodeGroup: d3.Selection<SVGGElement, unknown, any, undefined>;
let zoom = d3.zoom()
    .scaleExtent([0.1, 10])
    .on('zoom', zoomed);
let lastNodeX;
let intervalId: ReturnType<typeof setInterval> | null = null;
let animationSpeed = 800;

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

function isSmallerType(type: string) {
  return ["int", "long", "double", "float", "boolean", "char", "byte", "short"].includes(type);
}

function applyStep() {
  switch (inputStep.value) {
    case null:
    case undefined:
    case NaN:
      currentStep.value = 0;
      break;
    default:
      if (inputStep.value < 0) {
        currentStep.value = 0;
      } else if (inputStep.value > allNodes.value.reduce((max: any, n: any) => Math.max(max, n.id), 0)) {
        currentStep.value = allNodes.value.reduce((max: any, n: any) => Math.max(max, n.id), 0);
      } else {
        currentStep.value = inputStep.value;
      }
  }
  editMode.value = false;
}

function cancelEdit() {
  inputStep.value = currentStep.value;
  editMode.value = false;
}

function render() {

  const visibleNodes = getVisibleNodesAtStep(allNodes.value, currentStep.value);
  const visibleNodeIds = new Set(visibleNodes.map(n => n.elemId));
  const visibleLinks = Array.from(d3.group(
    allLinks.value.filter((l: any) =>
      l.visibleAt <= currentStep.value &&
      visibleNodeIds.has(l.source) &&
      visibleNodeIds.has(l.target)
    ),
    (d: any) => d.source + '-' + d.target
  ), ([_, group]) =>
    group.reduce((maxLink: any, current: any) => current.visibleAt > maxLink.visibleAt ? current : maxLink)
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
          group.selectAll('*').remove();
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
            // check if d.valuetype contains []
            if (d.valuetype === "java.lang.String") {
              renderStringNode(group, d);
            } else if (isSmallerType(d.valuetype)) {
              renderSmallTypeNode(group, d);
            } else if (d.valuetype === "List" || d.valuetype === "Array") {
              renderVerticalListNode(group, d);
            } else if (d.valuetype.includes('[]')) {
              renderHorizontalListNode(group, d);
            } else {
              renderObjectNode(group, d);
            }
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

  const nodeLinksMap = new Map(visibleNodes.map(node => [node.elemId, node]));
  const links = linkGroup.selectAll('.link')
    .data(visibleLinks, (d: any) => d.source + '-' + d.target)
    .join(
      enter => {
        const g = enter.append('line')
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
            });
        return g;
      },
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
      return src ? src.x : 0;
    })
    .attr('y1', (d: any) => {
      const src = nodeLinksMap.get(d.source);
      if (src.valuetype === "java.lang.String" || isSmallerType(src.valuetype) || src.valuetype.includes('[]')) {
        return src.y + 20;
      } else if (src.valuetype === "List" || src.valuetype === "Array" ) {
        return src.y + 30;
      }
      const objectfieldslength = heap.value?.find(obj => obj.id.toString() === src.label).fields.length || 0;
      const fieldOffset = (objectfieldslength > 3 ? 3.5 : objectfieldslength);
      return src.y + fieldOffset * 30 + 10;
    })
    .attr('x2', (d: any) => {
      // const src = nodeLinksMap.get(d.source);
      const tgt = nodeLinksMap.get(d.target);
      // return edgePoint(tgt, src).x;
      return tgt ? tgt.x : 0;
    })
    .attr('y2', (d: any) => {
      // const src = nodeLinksMap.get(d.source);
      const tgt = nodeLinksMap.get(d.target);
      // return edgePoint(tgt, src).y;
      return tgt ? tgt.y - 20 : 0;
    });
}

function renderStringNode(group: d3.Selection<SVGGElement, any, any, any>, d: any) {
  group.append('rect')
      .attr('x', -HALFWIDTH)
      .attr('y', -RADIUS - 5)
      .attr('width', WIDTH)
      .attr('height', (RADIUS + 5) * 2)
      .attr('rx', 5)
      .attr('ry', 5)
      .attr('stroke', '#333')
      .attr('stroke-width', 1.5)
      .attr('fill', d.color || '#cceeff');

  group.append('text')
      .text(() => {
        const str = d.label || d.string;
        if (str.length > 15) {
          return `"${str.substring(0, 15)}..."`;
        } else {
          return `"${str}"`;
        }
      })
      .attr('text-anchor', 'middle')
      .attr('dominant-baseline', 'middle')
      .attr('font-size', '16px');
}

function renderSmallTypeNode(group: d3.Selection<SVGGElement, any, any, any>, d: any) {
  const labelLength = d.label.toString().length;
  group.append('circle')
      .attr('r', RADIUS + 5)
      .attr('stroke', '#333')
      .attr('stroke-width', 1.5)
      .attr('fill', d.color);
  group.append('text')
      .text(d.label)
      .attr('text-anchor', 'middle')
      .attr('dominant-baseline', 'middle')
      .attr('font-size', circleFontSize.get(labelLength));
}

function renderObjectNode(group: d3.Selection<SVGGElement, any, any, any>, d: any) {
  const object = heap.value?.find(obj => obj.id.toString() === d.label);
  var fields = object?.fields || [];
  var result = fields;
  if (fields.length > 3) {
    // search for field with name "name", "Name", "title" or "Title"
    const idField = fields.find(f => ['id', 'Id', 'ID'].includes(f.name));
    const nameField = fields.find(f => ['name', 'Name', 'title', 'Title'].includes(f.name));
    result = [idField, nameField].filter(Boolean);
    fields = fields.slice(0, 3 - result.length);
    fields = fields.filter(f => f !== idField && f !== nameField);
    result = result.concat(fields);
    result.push({name: '...', value: {type: '...', value: '...'}});
  }

  group.append('rect')
      .attr('x', -HALFWIDTH)
      .attr('y', -RADIUS - 5)
      .attr('width', WIDTH)
      .attr('height', (RADIUS) * 2)
      .attr('rx', 2)
      .attr('ry', 2)
      .attr('stroke', '#333')
      .attr('stroke-width', 1.5)
      .attr('fill', d.color);

  group.append('text')
      .text(d.valuetype)
      .attr('text-anchor', 'middle')
      .attr('dominant-baseline', 'middle');

  for (let i = 0; i < result.length; i++) {
    // height is (RADIUS + 5) + 10 normally but if value is ... then make it smaller
    const isEllipsis = result[i].name === '...';
    const height = isEllipsis ? RADIUS : (RADIUS + 5) + 10;
    group.append('rect')
        .attr('x', -HALFWIDTH)
        .attr('y', 10 * (i + 1) + i * (RADIUS + 5))
        .attr('width', (RADIUS) * 5)
        .attr('height', height)
        .attr('rx', 2)
        .attr('ry', 2)
        .attr('stroke', '#333')
        .attr('stroke-width', 1.5)
        .attr('fill', d3.color(d.color)?.copy({opacity: 0.2})?.toString() || d.color);

    group.append('text')
        .text(d => result[i].name)
        .attr('text-anchor', 'middle')
        .attr('dominant-baseline', 'middle')
        .attr('y', isEllipsis ? height * 7 : height * (i + 1))
        .attr('x', -HALFWIDTH / 2)
        .attr('font-size', '16px');

    group.append('rect')
        .attr('x', 0)
        .attr('y', 10 * (i + 1) + i * (RADIUS + 5))
        .attr('width', HALFWIDTH)
        .attr('height', height)
        .attr('rx', 2)
        .attr('ry', 2)
        .attr('stroke', '#333')
        .attr('stroke-width', 1.5)
        .attr('fill', d3.color(d.color)?.copy({opacity: 0.2})?.toString() || d.color);

    group.append('text')
        .text(d => {
          const object = result[i].value;
          console.log('Heap:');
          console.log(heap.value);
          if (object.reference) {
            let refobject = heap.value?.find(obj => obj.id === object.reference);
            let t = refobject?.string;

            if (!t) {
              let type = refobject.type;
              t = type.substring(type.lastIndexOf('.') + 1, type.length);
            }

            if (t.length > 10) {
              t = t.substring(0, 10) + "...";
            }

            return t;
          } else if (object.primitiveValue) {
            return object.primitiveValue;
          } else if (object.value) {
            return object.value;
          }
          return '';
        })
        .attr('text-anchor', 'middle')
        .attr('dominant-baseline', 'middle')
        .attr('y', isEllipsis ? height * 7 : height * (i + 1))
        .attr('x', HALFWIDTH / 2)
        .attr('font-size', '16px');
  }
}

function renderVerticalListNode(group: d3.Selection<SVGGElement, any, any, any>, d: any) {
  let labelAsList = d.label.split(',');
  let length = labelAsList.length;
  let firstElem = allNodes.value.find(n => n.elemId.toString() === labelAsList[0]);
  let typeOfElements = firstElem?.valuetype || 'Unknown';
  if (typeOfElements.endsWith('[]')) {
    typeOfElements = typeOfElements.substring(0, typeOfElements.length - 2);
  }
  let elemHeight = (RADIUS + 5) * 2 + MARGIN;
  if (!isSmallerType(typeOfElements) && typeOfElements !== "java.lang.String") {
    const elem = heap.value?.find(obj => obj.id.toString() === firstElem.label);
    if (elem) {
      const fieldsLength = elem.fields.length;
      elemHeight = Math.min(fieldsLength, 3) * (RADIUS + 5) * 2 + MARGIN * 2;
    }
  }

  const subGroup = group.append('g');

  for (let i = 0; i < length; i++) {
    const elemNode = allNodes.value.find(n => n.elemId.toString() === labelAsList[i].trim());
    const elemGroup = subGroup.append('g')
        .attr('transform', `translate(0, ${MARGIN + i * elemHeight})`);
    if (elemNode.valuetype === "java.lang.String") {
      renderStringNode(elemGroup, elemNode);
    } else if (isSmallerType(elemNode.valuetype)) {
      renderSmallTypeNode(elemGroup, elemNode);
    } else if (elemNode.valuetype.includes('[]')) {
      renderHorizontalListTerminalNode(elemGroup, elemNode);
    }  else {
      renderObjectNode(elemGroup, elemNode);
    }
  }

  const bbox = subGroup.node()?.getBBox();
  let width = WIDTH + MARGIN * 2, height = elemHeight * length + MARGIN;
  if (bbox) {
    width = Math.max(bbox.width + MARGIN * 2, WIDTH + MARGIN * 2);
    height = bbox.height + MARGIN * 2;
  }

  group.insert('rect', ':first-child')
      .attr('x', -HALFWIDTH - MARGIN)
      .attr('y', -RADIUS - 5)
      .attr('width', width)
      .attr('height',  height)
      .attr('rx', 5)
      .attr('ry', 5)
      .attr('stroke', '#333')
      .attr('stroke-width', 1.5)
      .attr('fill', d.color);
}

function renderHorizontalListNode(group: d3.Selection<SVGGElement, any, any, any>, d: any) {
  let labelAsList = d.label.split(',');
  let elem = heap.value.find(n => n.id.toString() === labelAsList[0]);
  let elems = elem.elements;
  let length = elems.length;
  if (length > 5) {
    length = 5;
    elems = [elem.elements[0], elem.elements[1], {name: '...', value: { primitiveValue: '...' }}, elem.elements[elem.elements.length - 2],
      elem.elements[elem.elements.length - 1]];
  }
  let height = (RADIUS + 5) * 2;
  const type = elem.type.endsWith('[]') ?
      elem.type.substring(0, elem.type.length - 2) :
      elem.type;
  if (!isSmallerType(type) && type !== "java.lang.String") {
    const firstElemNode = heap.value.find(n => n.id === elems[0].value.reference);
    if (firstElemNode) {
      const fieldsLength = firstElemNode.fields.length;
      height = Math.min(fieldsLength, 3) * (RADIUS + 5) * 2 + MARGIN * 2;
    }
  }

  group.append('rect')
      .attr('x', -HALFWIDTH - 2)
      .attr('y', -RADIUS - 5)
      .attr('width', WIDTH + 4)
      .attr('height', height)
      .attr('rx', 5)
      .attr('ry', 5)
      .attr('stroke', '#333')
      .attr('stroke-width', 1.5)
      .attr('fill', d.color);

  for (let i = 0; i < length; i++) {
    let elemNode = heap.value.find(n => n.id === elems[i].value.reference);
    if (!elemNode) {
      group.append('circle')
          .attr('cx', -HALFWIDTH + i * (WIDTH / length) + (WIDTH / length) / 2)
          .attr('r', RADIUS - 1)
          .attr('stroke', '#333')
          .attr('stroke-width', 1.5)
          .attr('fill', '#f0f0f0');
      group.append('text')
          .text(elems[i].value.primitiveValue)
          .attr('text-anchor', 'middle')
          .attr('dominant-baseline', 'middle')
          .attr('x', -HALFWIDTH + i * (WIDTH / length) + (WIDTH / length) / 2)
          .attr('font-size', miniCircleFontSize.get(elems[i].value.primitiveValue.toString().length) || '10px');
    } else {
      console.log('Rendering element node inside horizontal list:');
      console.log(elemNode);
      if (elemNode.type === "java.lang.String") {
        group.append('rect')
            .attr('x', -HALFWIDTH + i * (WIDTH / length) + (WIDTH / length) / 2 - HALFWIDTH / length)
            .attr('y', -RADIUS)
            .attr('width', WIDTH/length)
            .attr('height', (RADIUS - 1) * 2)
            .attr('rx', 5)
            .attr('ry', 5)
            .attr('stroke', '#333')
            .attr('stroke-width', 1.5)
            .attr('fill', '#f0f0f0');
        group.append('text')
            .text(() => {
              const str = elemNode.label || elemNode.string;
              let maxLength = miniStringLength.get(length) || 3;
              if (str.length > maxLength) {
                return `"${str.substring(0, maxLength)}..."`;
              } else {
                return `"${str}"`;
              }
            })
            .attr('text-anchor', 'middle')
            .attr('dominant-baseline', 'middle')
            .attr('x', -HALFWIDTH + i * (WIDTH / length) + (WIDTH / length) / 2)
            .attr('font-size', '9px');
      }
    }
  }
}

function renderHorizontalListTerminalNode(group: d3.Selection<SVGGElement, any, any, any>, d: any) {
  let labelAsList = d.label.split(',');
  let elem = heap.value.find(n => n.id.toString() === labelAsList[0]);
  let elems = elem.elements;
  let length = elems.length;
  let stringlength = 0;

  for (let i = 0; i < length; i++) {
    let elemNode = heap.value.find(n => n.id === elems[i].value.reference);
    if (!elemNode) {
      const cx = -HALFWIDTH + (i + 1) * RADIUS * 2 - RADIUS;
      group.append('circle')
          .attr('cx', cx)
          .attr('r', RADIUS - 1)
          .attr('stroke', '#333')
          .attr('stroke-width', 1.5)
          .attr('fill', '#f0f0f0');
      group.append('text')
          .text(elems[i].value.primitiveValue)
          .attr('text-anchor', 'middle')
          .attr('dominant-baseline', 'middle')
          .attr('x', cx)
          .attr('font-size', miniCircleFontSize.get(elems[i].value.primitiveValue.toString().length) || '10px');
    } else {
      if (elemNode.type === "java.lang.String") {
        const label = elemNode.label || elemNode.string;
        const textValue = '"' + label + '"';

        const tempText = group.append('text')
            .text(textValue)
            .attr('font-size', '9px')
            .attr('visibility', 'hidden'); // unsichtbar, aber messbar

        const textWidth = tempText.node().getBBox().width;

        const x = -HALFWIDTH + stringlength + MARGIN / 2;
        const rectWidth = textWidth + MARGIN; // bisschen Puffer

        group.append('rect')
            .attr('x', x)
            .attr('y', -RADIUS)
            .attr('width', rectWidth)
            .attr('height', (RADIUS - 1) * 2)
            .attr('rx', 5)
            .attr('ry', 5)
            .attr('stroke', '#333')
            .attr('stroke-width', 1.5)
            .attr('fill', '#f0f0f0');

        group.append('text')
            .text(textValue)
            .attr('text-anchor', 'left')
            .attr('dominant-baseline', 'middle')
            .attr('x', x + MARGIN / 2)
            .attr('font-size', '9px');

        tempText.remove();
        stringlength += rectWidth;
      }
    }
  }

  const bbox = group.node()?.getBBox();
  const width = bbox ? Math.max(bbox.width + MARGIN, WIDTH) : WIDTH;

  group.insert('rect', ':first-child')
      .attr('x', -HALFWIDTH - 2)
      .attr('y', -RADIUS - 5)
      .attr('width', width)
      .attr('height', (RADIUS + 5) * 2)
      .attr('rx', 5)
      .attr('ry', 5)
      .attr('stroke', '#333')
      .attr('stroke-width', 1.5)
      .attr('fill', d.color);

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

function playThrough() {
  if (isPlaying.value) {
    return;
  }
  currentStep.value = 0;
  render();
  play();
}

function playFrom() {
  if (isPlaying.value) {
    return;
  }
  play();
}

function play() {
  isPlaying.value = true;
  intervalId = setInterval(() => {
    if (isPlaying && currentStep.value < allNodes.value.reduce((max: any, n: any) => Math.max(max, n.id), 0)) {
      currentStep.value++;
    } else {
      stopPlaying()
    }
  }, animationSpeed);
}

function stopAndResetPlaying() {
  currentStep.value = 0;
  stopPlaying();
}

function stopPlaying() {
  isPlaying.value = false;
  if (intervalId) {
    clearInterval(intervalId);
    intervalId = null;
  }
}

function zoomed(event: any) {
  container.attr('transform', event.transform);
}

function zoomAndCenter() {
  if (!svg || !container) return;

  const { width: svgWidth, height: svgHeight } = svg.node().getBoundingClientRect();

  const bounds = container.node().getBBox();
  const fullWidth = bounds.width;
  const fullHeight = bounds.height;

  const midX = bounds.x + fullWidth / 2;
  const midY = bounds.y + fullHeight / 2;

  if (fullWidth === 0 || fullHeight === 0) return; // Avoid division by zero

  const scale = Math.min(svgWidth / fullWidth, svgHeight / fullHeight) * 0.9; // Slightly smaller to fit

  const translateX = svgWidth / 2 - scale * midX;
  const translateY = svgHeight / 2 - scale * midY;

  const transform = d3.zoomIdentity
      .translate(translateX, translateY)
      .scale(scale);

  svg.call(zoom.transform, transform);
}

watch(streamVizInfo, (newVal, oldVal) => {
  stopPlaying();
  if (streamVizInfo.value.marbles.length > 0 && oldVal && JSON.stringify(oldVal) !== JSON.stringify(newVal)) {
    currentStep.value = 0;
    nextTick(() => {
      svg = d3.select('#stream-viz-svg');
      svg.selectAll('*').remove();

      svg.append('rect')
          .attr('class', 'zoom-bg')
          .attr('width', '100%')
          .attr('height', '100%')
          .attr('fill', 'transparent')
          .style('pointer-events', 'all');

      container = svg.append('g').attr('class', 'container');

      lastNodeX = Math.max(...allNodes.value.map((n: any) => n.x));
      svg.append('defs').append('marker')
        .attr('id', 'arrow')
        .attr('viewBox', '0 -5 10 10')
        .attr('refX', 10)
        .attr('refY', 0)
        .attr('markerWidth', 8)
        .attr('markerHeight', 10)
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
        .attr('x2', lastNodeX + 210)
        .attr('y2', (d: any) => d.y)
        .attr('stroke', '#999')
        .attr('stroke-width', 1.5)
        .attr('stroke-dasharray', '8 4');

      container.selectAll('.oplabel')
        .data(Object.values({ ...operationLines.value }))
        .join('text')
        .attr('x', lastNodeX + 200)
        .attr('y', (d: any) => d.y - 5)
        .attr('text-anchor', 'end')
        .text((d: any) => d.type)
        .attr('fill', '#555')
        .attr('font-size', '24px');

      const paramLabelGroups = container.selectAll('.param-label-group')
          .data(Object.values({ ...operationLines.value }))
          .join(
              enter => {
                const g = enter.append('g')
                    .attr('class', 'param-label-group')
                    .attr('transform', (d: any) => `translate(${lastNodeX + 150}, ${d.y + 25})`); // <<-- Verschiebt die ganze Gruppe

                // Füge ein leeres Text-Element hinzu, um die tspan-Elemente aufzunehmen
                g.append('text')
                    .attr('class', 'param-label')
                    .attr('text-anchor', 'start')
                    .attr('fill', '#666') // Etwas dunkler als #888, aber heller als der Op-Name
                    .attr('font-size', '16px')
                    .style('font-family', 'Monospace, Courier New, monospace')
                    .attr('xml:space', 'preserve')
                    .style('pointer-events', 'none'); // Text selbst ignoriert Events

                return g;
              },
              update => {
                update.attr('transform', (d: any) => `translate(${lastNodeX + 10}, ${d.y + 25})`);
                return update;
              },
              exit => exit.remove()
          );


      paramLabelGroups.each(function(d: any) {
        const group = d3.select(this);
        const textElement = group.select('.param-label');
        textElement.selectAll('tspan').remove();

        const lines = d.param.replace(/\\n/g, '\n').split('\n');

        lines.forEach((line: string, i: number) => {
          textElement.append('tspan')
              .attr('x', 0)
              .attr('dy', i === 0 ? 0 : '1.2em')
              .text(line);
        });

        const bbox = textElement.node()?.getBBox();

        if (bbox && bbox.width > 0 && bbox.height > 0) {
          group.select('.param-bg-rect').remove(); // Altes Rechteck entfernen, falls vorhanden

          group.insert('rect', '.param-label') // Fügt das Rechteck VOR dem Text ein
              .attr('class', 'param-bg-rect')
              .attr('x', bbox.x - 5) // Etwas Polsterung links
              .attr('y', bbox.y - 3) // Etwas Polsterung oben
              .attr('width', bbox.width + 10) // Polsterung links+rechts
              .attr('height', bbox.height + 6) // Polsterung oben+unten
              .attr('rx', 4) // Abgerundete Ecken
              .attr('ry', 4)
              .attr('fill', '#f0f0f0') // Hellgrau
              .attr('opacity', 0.8) // Leicht transparent
              .style('pointer-events', 'none'); // Ignoriert Mausereignisse
        } else {
          // Falls bbox leer ist (z.B. d.param ist leer), das Rechteck entfernen
          group.select('.param-bg-rect').remove();
        }
      });

      linkGroup = container.append('g').attr('class', 'links');
      nodeGroup = container.append('g').attr('class', 'nodes');

      svg.call(zoom);
      render();
      zoomAndCenter();
    });
  }
});

watch(currentStep, () => {
  render();
});

watch(editMode, (val) => {
  if (val) {
    inputStep.value = currentStep.value;
  }
  nextTick(() => {
    stepInput.value?.focus();
    stepInput.value?.select();
  })
});

onUnmounted(() => {
  stopPlaying();
})
</script>

<style>
.link {
  fill: none;
  stroke: #999;
  stroke-width: 2px;
  marker-end: url(#arrow);
}
.node circle {
  //stroke: #333;
  stroke-width: 1.5px;
  padding: 5px;
}
text {
  user-select: none;
}
.controls {
  margin-top: 20px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 20px;
}
.myButton {
  font-size: 1.5rem;
  font-weight: bold;
  border-radius: 15px;
  border: 2px solid black;
  background-color: #efefef;
  padding: 5px 15px 5px 15px;
  cursor: pointer;
}

.myButton:hover {
  background-color: grey;
  box-shadow: 0px 0px 15px grey;
}

.playControls {
  display: flex;
  gap: 10px;
  align-items: center;
}

.playControls button {
  font-size: 1.2rem;
  padding: 5px 13px 5px 12px;
  border-radius: 15px;
  border: 2px solid black;
  background-color: #efefef;
  cursor: pointer;
}

.playButton {
  color: green;
}

.playButton:hover {
  background-color: lightgreen;
  color: black;
}

.stopButton {
  color: red;
}

.stopButton:hover {
  background-color: lightcoral;
  color: black;
}

.inputField {
  width: 3.5rem;
  font-size: 1.2rem;
  text-align: center;
  border: 2px solid grey;
  border-radius: 5px;
  padding: 5px 0px;
}

.inputField:focus {
  outline: none;
  border-color: grey;
  box-shadow: 0 0 5px grey;
}

</style>
