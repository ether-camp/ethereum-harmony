var BlockchainView = (function () {

    var GAP = 14;
    var BLOCK_WIDTH = 80;
    var BLOCK_HEIGHT = 28;

    var NUMBERING_WIDTH = 120;

    var MAX_BLOCK_COUNT = 50;

    /**
     * Map of block hash to block object.
     * @type {{String, Block}}
     */
    var blockStore = {};

    // for local debug
    var selfTodoRemove = null;

    /**
     * Number of rows to render.
     * @type {Array}
     */
    var blockNumbers = [];
    var minBlockNumber = 0;

    /**
     * List of blocks added from outside.
     * @type {Array}
     */
    var rawData = [];

    /**
     * Grid (2 dimension array) of blocks to be rendered at that location.
     * Indexing order, for example [[b00, b01],[b10, b11]] will be rendered:
     * b01 b11
     * b00 b10
     * Array order is matter, but sub-array index doesn't. Block.index is used for proper location inside column.
     * @type {Array}
     */
    var renderColumns = [];

    /**
     * Block which user rolled over with mouse.
     * @type {Block}
     */
    var mouseOverBlockHash = null;

    var svgContainer = null;

    var blockHashFun = function(b) {return b.blockHash};
    var hashCompareFun = function(hash) { return function(b) {return b.blockHash == hash}};

    /**
     * Complex function for removing old blocks.
     */
    function checkForRemove() {
        if (rawData.length > MAX_BLOCK_COUNT) {
            var blockForRemove = rawData.shift();
            renderColumns.some(function(c) {
                return c.some(function(b, i) {
                    if (b && b.blockHash == blockForRemove.blockHash) {
                        delete c[i];
                        delete blockStore[blockForRemove.blockHash];
                        //console.log('Removed from grid ' + blockForRemove.blockHash);
                        return true;
                    }
                    return false;
                })
            });
            var isLineEmpty = renderColumns.every(function(c) {
                return !c[0];
            });
            if (isLineEmpty) {
                //console.log('Removing bottom layer');
                renderColumns.forEach(function(c) {
                    if (c.length > 0) {
                        c.unshift();
                    }
                });
            }
        }
    }

    /**
     * Complex function for adding new blocks.
     */
    function prepareAndAddBlocks(newBlocks) {
        newBlocks.forEach(function(b) {
            if (!blockStore[b.blockHash]) {
                blockStore[b.blockHash] = b;

                rawData.push(b);
                checkForRemove();
            } else {
                console.log('Rejected duplicate block ' + b.blockHash);
            }
        });

        blockNumbers = [];
        rawData.forEach(function(b) {
            if (blockNumbers.indexOf(b.blockNumber) < 0) {
                blockNumbers.push(b.blockNumber);
            }
        });
        // default sort, looks sort values as strings
        // force number sorting
        blockNumbers.sort(function(a,b) {return a - b;});
        minBlockNumber = blockNumbers[0];

        rawData.forEach(function(b) {
            b.isCanonical = false;
            b.index = b.blockNumber - minBlockNumber;
            b.totalDifficulty = 0;
        });

        // calculate total difficulty per each leaf
        rawData.forEach(function(b) {
            var parentBlock = blockStore[b.parentHash];
            if (parentBlock) {
                b.totalDifficulty = b.difficulty + (parentBlock.totalDifficulty || 0);
            } else {
                b.totalDifficulty = b.difficulty;
            }
        });

        var canonicalLeaf = rawData.reduce(function(result, b) {
            return b.totalDifficulty > result.totalDifficulty ? b : result;
        }, rawData[0]);
        var canonicalColumn = rawData
            .reverse()
            .reduce(function(column, b) {
                if (b.blockHash == column[0].parentHash) {
                    column.unshift(b);
                }
                return column;
            }, [canonicalLeaf]);
        rawData.reverse(); // back
        canonicalColumn.forEach(function(b) {
            b.isCanonical = true;
        });

        //console.log('Canonical chain ' + canonicalColumn.map(blockHashFun));
    }

    function point(x, y) {
        return {x: x, y: y};
    }

    /**
     * Draw canonical bracket marker (left or right)
     * @param mW - parameter for left(0) or right(1) side bracket
     * @param mS - direction of bracket: to right(-1) or to left(1)
     * @returns {Function}
     */
    function markerLineData(mW, mS) {
        return function(block) {
            var g = 4;
            var side = 6;
            var x = block.x;
            var y = block.y;
            var w = block.width;
            var h = block.height;

            var xOffset = x + mS * g + w * mW;

            return [
                point(xOffset - mS * side,    y - g),
                point(xOffset,                y - g),
                point(xOffset,                y + g + h),
                point(xOffset - mS * side,    y + g + h)
            ];
        }
    }

    var markerLeftLineData      = markerLineData(0, -1);
    var markerRightLineData     = markerLineData(1, 1);

    var sampleBlock              = {x: 0, y:0, width: BLOCK_WIDTH, height: BLOCK_HEIGHT};
    var leftBracketData         = markerLeftLineData(sampleBlock);
    var rightBracketData        = markerRightLineData(sampleBlock);

    // Marking canonical
    var lineFunction = d3.svg
        .line()
        .x(function(d) { return d.x; })
        .y(function(d) { return d.y; })
        .interpolate('linear');
    var leftBracketLine = lineFunction(leftBracketData);
    var rightBracketLine = lineFunction(rightBracketData);


    /**
     * Place block to grid
     * @param block
     */
    function placeBlock(block) {
        //console.log('addBlock ' + block.blockHash);
        function sortByIndex(array) {
            array.sort(function(a,b) {return a.index - b.index;});
        }

        if (renderColumns.length == 0) {
            var newColumn = [];
            newColumn[block.index] = block;
            // assume the first block is canonical, and put it in the middle
            renderColumns.push([]);
            renderColumns.push(newColumn);
            //console.log('Added initial column for block ' + block.blockHash);
        } else {
            var putOverParent = renderColumns.some(function(column) {
                return column.some(function(b) {
                    if (b.blockHash == block.parentHash) {
                        var blockOverParent =  column.some(function(bb) {return bb.index == block.index});
                        if (!blockOverParent) {
                            column.push(block);
                            sortByIndex(column);
                            //console.log('Put block over parent ' + block.blockHash);
                            return true;
                        }
                    }
                    return false;
                });
            });
            if (!putOverParent) {

                var putInFreeSpace = renderColumns.some(function(column) {
                    var blockAtPlace =  column.some(function(bb) {return bb.index == block.index});
                    if (!blockAtPlace) {
                        column.push(block);
                        sortByIndex(column);
                        //console.log('Added block to free space ' + block.blockHash);
                        return true;
                    }
                    return false;
                });

                if (!putInFreeSpace) {
                    var newColumn = [];
                    newColumn[block.index] = block;
                    renderColumns.push(newColumn);
                    //console.log('New column created to put block ' + block.blockHash);
                }
            }
        }
    }

    /**
     * @param renderColumns - current state
     */
    function renderState(svgContainer, width, height) {

        //console.log('renderColumns ' + renderColumns.length);

        //var blockWidth = Math.min(MAX_BLOCK_WIDTH, (width - NUMBERING_WIDTH - GAP * (columnCount + 1)) / columnCount);
        //console.log('Block width ' + blockWidth);

        renderColumns
            .forEach(function(column, c) {
                column.forEach(function(block, n) {
                    if (block) {
                        //console.log('Adding block for rendering ' + block);
                        block.text      = '0x' + block.blockHash.substr(0, 4);
                        block.x         = NUMBERING_WIDTH + GAP * (c + 1) + BLOCK_WIDTH * c;
                        block.y         = height - GAP * (block.index + 1) - BLOCK_HEIGHT * (block.index + 1);
                        block.width     = BLOCK_WIDTH;
                        block.height    = BLOCK_HEIGHT;
                    }
                });
            });

        rawData.forEach(function(block) {
            var parentBlock = blockStore[block.parentHash];
            if (parentBlock) {
                block.parentX = parentBlock.x;
                block.parentY = parentBlock.y;
            }
            block.hasParent = parentBlock != null;
        });

        var blockNumberObjects = blockNumbers
            .map(function(blockNumber, i) {
                return {
                    x:      0,
                    y:      height - GAP * (i + 1) - BLOCK_HEIGHT * (i + 1),
                    width:  NUMBERING_WIDTH - 2 * GAP,
                    height: BLOCK_HEIGHT,
                    text:   blockNumber
                };
            });


        svgContainer
            .attr('width', width)
            .attr('height', height);
            //.selectAll('*')
            //.remove();

        var lineContainer = svgContainer.select('#lineContainer');
        var blockContainer = svgContainer.select('#blockContainer');
        var numberingContainer = svgContainer.select('#numberingContainer');
        var canonicalContainer = svgContainer.select('#canonicalContainer');

        // Blocks
        var blockSelection = blockContainer
            .selectAll('g')
            .data(rawData, blockHashFun);

        blockSelection
            .exit()
            .remove();

        var blockGroupSelection = blockSelection
            .enter()
            .append('g');

        blockGroupSelection
            .attr('opacity', 1)
            .attr("transform", function(d) { return "translate(" + d.x + "," + (d.y - BLOCK_HEIGHT - GAP) + ")"})
            .on('mouseover', function(d, i) {
                mouseOverBlockHash = d.blockHash;
                drawParentLines(svgContainer, width, height);
            })
            .on('mouseout', function(d, i) {
                mouseOverBlockHash = null;
                drawParentLines(svgContainer, width, height);
            });

        blockGroupSelection
            .append('rect')
            .attr('x', 0)
            .attr('y', 0)
            .attr('width', BLOCK_WIDTH)
            .attr('height', BLOCK_HEIGHT)
            .style('fill', '#5E9CD3')
            .style('stroke-opacity', 0)
            .style('stroke', '#41719C')
            .style('stroke-width', 2)
            .on('click', function(d, i) {
                //handle events here
                //d - datum
                //i - identifier or index
                //this - the `<rect>` that was clicked
                //selfTodoRemove.addBlocks([{
                //    blockHash : 'R' + Math.random().toString(),
                //    blockNumber : d.blockNumber,
                //    difficulty : d.difficulty + 1,
                //    parentHash : d.parentHash
                //}]);
            })
            .attr('opacity', 0)
            .transition()
            .duration(1000)
            .attr('opacity', 1);

        blockGroupSelection
            .append('text')
            .attr('x', BLOCK_WIDTH / 2)
            .attr('y', 20)
            .text( function (d) { return d.text; })
            .attr('font-family', 'sans-serif')
            .attr('text-anchor', 'middle')
            .attr('font-size', '16px')
            .attr('fill', '##5E9CD3')
            .attr('opacity', 0)
            .transition()
            .duration(1000)
            .attr('opacity', 1);

        blockSelection
            .transition()
            .duration(1000)
            .attr('opacity', 1)
            .attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"});

        var canonicalSelection = canonicalContainer
            .selectAll("g")
            .data(
                rawData.filter(function(d) { return d.isCanonical; }),
                function(d) { return d.index; });

        canonicalSelection
            .exit()
            .attr('opacity', 1)
            .transition()
            .duration(1000)
            .attr('opacity', 0)
            .remove();

        var canonicalGroupSelection = canonicalSelection
            .enter()
            .append('g');

        canonicalGroupSelection
            .attr("transform", function(d) { return "translate(" + d.x + "," + (d.y - BLOCK_HEIGHT - GAP) + ")"})
            .attr('opacity', 0);

        canonicalGroupSelection
            .append('path')
            .attr('stroke', '#FFD966')
            .attr('stroke-width', 3)
            .attr('d', leftBracketLine)
            .attr('fill', 'none');

        canonicalGroupSelection
            .append('path')
            .attr('stroke', '#FFD966')
            .attr('stroke-width', 3)
            .attr('d', rightBracketLine)
            .attr('fill', 'none');


        canonicalSelection
            .transition()
            .duration(1000)
            .attr('opacity', 1)
            .attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"});


        // NUMBERS
        var numberingSelection = numberingContainer
            .selectAll('text')
            .data(blockNumberObjects, function(d) { return d.text; });

        numberingSelection
            .exit()
            .remove();

        numberingSelection
            .enter()
            .append('text')
            .attr('x', function(d) { return d.x + NUMBERING_WIDTH / 2; })
            .attr('y', function(d) { return d.y + 20 - BLOCK_HEIGHT - GAP; })
            .text( function (d) { return d.text; })
            .attr('font-family', 'sans-serif')
            .attr('text-anchor', 'middle')
            .attr('font-size', '16px')
            .attr('fill', '#C5E0B4')
            .attr('opacity', 0);

        numberingSelection
            .attr('fill', '#C5E0B4')
            .transition()
            .duration(1000)
            .attr('y', function(d) { return d.y + 20; })
            .attr('opacity', 1);

        // BLUE VERTICAL LINE
        numberingContainer.select('#blueLine').remove();
        numberingContainer
            .append('path')
            .attr('id', 'blueLine')
            .attr('d', lineFunction([
                point(NUMBERING_WIDTH, 0),
                point(NUMBERING_WIDTH, height)
            ]))
            .attr('stroke', '#41719C')
            .attr('stroke-width', 2)
            .attr('fill', 'none');

        drawParentLines(svgContainer, width, height);
    }

    function drawParentLines(svgContainer, width, height) {
        var lineContainer = svgContainer.select('#lineContainer');
        var mouseOverBlock = rawData.find(hashCompareFun(mouseOverBlockHash));
        if (!mouseOverBlock) {
            lineContainer
                .selectAll('*')
                .remove();
            return;
        }

        var blockLinesObjects = rawData
            .reverse()
            .reduce(function(blocks, b) {
                if (blocks[0].parentHash == b.blockHash) {
                    blocks.unshift(b);
                }
                return blocks;
            }, [mouseOverBlock]);
        rawData.reverse();  // back

        blockLinesObjects.shift();
        //console.log(blockLinesObjects.map(blockHashFun));

        var lineSelection = lineContainer
            .selectAll('path')
            .data(
                blockLinesObjects
                    .filter(function(d) {
                        return d.hasParent;
                    }),
                blockHashFun);

        lineSelection
            .enter()
            .append('path')
            .attr('stroke', '#C5E0B4')
            .attr('stroke-width', 1)
            .attr('fill', 'none')
            .style('stroke-dasharray', ('2, 2'));

        lineSelection
            .exit()
            .remove();

        lineSelection
            .transition()
            .duration(1000)
            .attr('d', function(d) {
                return lineFunction([point(d.x + BLOCK_WIDTH / 2, d.y + BLOCK_HEIGHT / 2), point(d.parentX + BLOCK_WIDTH / 2, d.parentY + BLOCK_HEIGHT / 2)]);
            });
    }

    function ChartComponent(element, config) {

        config = config || {};
        var width = config.width || 400;
        var height = config.height || 400;

        svgContainer = d3.select(element)
            .append('svg');
        svgContainer
            .append('g')
            .attr('id', 'lineContainer');
        svgContainer
            .append('g')
            .attr('id', 'blockContainer');
        svgContainer
            .append('g')
            .attr('id', 'numberingContainer');
        svgContainer
            .append('g')
            .attr('id', 'canonicalContainer');


        var self = selfTodoRemove = this;

        /**
         * @data - component creates copy
         */
        self.addBlocks = function(data) {
            data = data || [];

            if (data.length == 0) {
                return;
            }
            console.log('Adding ' + data.length + ' new blocks');

            // deep clone
            data = jQuery.extend(true, [], data);

            prepareAndAddBlocks(data);

            var numbersCount = blockNumbers.length;
            var requiredHeight = Math.max(BLOCK_HEIGHT * numbersCount + GAP * (numbersCount + 1), height);

            data.forEach(function(b) {
                placeBlock(b);
            });

            var colCount = renderColumns.length;
            var requiredWidth = Math.max(width, NUMBERING_WIDTH + BLOCK_WIDTH * colCount + GAP * (colCount + 1));
            //var renderColumns = addBlockViaSorting(data);

            renderState(svgContainer, requiredWidth, requiredHeight);
            return self;
        };

        return self;
    }

    return {
        create: function(element, config) {
            return new ChartComponent(element, config);
        }
    }
})();