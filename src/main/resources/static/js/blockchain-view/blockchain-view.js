var BlockchainView = (function () {

    var GAP = 14;
    var BLOCK_HEIGHT = 28;
    var NUMBERING_WIDTH = 120;
    var MIN_BLOCK_WIDTH = 60;
    var MAX_BLOCK_WIDTH = 80;

    var unique = function(xs) {
        var seen = {};
        return xs.filter(function(x) {
            if (seen[x])
                return;
            seen[x] = true;
            return x;
        })
    };

    var blockHashFun = function(b) {return b.blockHash};


    function prepareData(data) {
        var blockStore = {};
        var parentsMap = {};
        var blockNumbers = [];

        data.forEach(function(b) {
            parentsMap[b.blockHash] = true;
            blockStore[b.blockHash] = b;
            if (blockNumbers.indexOf(b.blockNumber) < 0) {
                blockNumbers.push(b.blockNumber);
            }
        });
        blockNumbers.sort();
        var minBlockNumber = blockNumbers[0];
        data.forEach(function(b) {
            b.index = b.blockNumber - minBlockNumber;
        });


        // calculate total difficulty per each leaf
        data.forEach(function(b) {
            var parentBlock = blockStore[b.parentHash];
            if (parentBlock) {
                b.totalDifficulty = b.difficulty + (parentBlock.totalDifficulty || 0);
            } else {
                b.totalDifficulty = b.difficulty;
            }
        });

        var canonicalLeaf = data.reduce(function(result, b) {
            return b.totalDifficulty > result.totalDifficulty ? b : result;
        }, data[0]);
        var canonicalColumn = data
            .reverse()
            .reduce(function(column, b) {
                if (b.blockHash == column[0].parentHash) {
                    column.unshift(b);
                }
                return column;
            }, [canonicalLeaf]);
        data.reverse(); // back
        canonicalColumn.forEach(function(b) {
            b.isCanonical = true;
        });

        //console.log('Canonical chain ' + canonicalColumn.map(blockHashFun));

        return blockNumbers;
    }

    function point(x, y) {
        return {x: x, y: y};
    }

    /**
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

    /**
     * @param columns - state
     * @param block
     */
    function addBlock(columns, block) {
        //console.log('addBlock ' + block.blockHash);

        if (columns.length == 0) {
            var newColumn = [];
            newColumn[block.index] = block;
            // assume the first block is canonical, and put it in the middle
            columns.push([]);
            columns.push(newColumn);
            //console.log('Added initial column for block ' + block.blockHash);
        } else {
            var putOverParent = columns.some(function(column) {
                return column.some(function(b) {
                    if (b.blockHash == block.parentHash) {
                        if (!column[block.index]) {
                            column[block.index] = block;
                            //console.log('Put block over parent ' + block.blockHash);
                            return true;
                        }
                    }
                    return false;
                });
            });
            if (!putOverParent) {

                var putInFreeSpace = columns.some(function(column) {
                    if (!column[block.index]) {
                        column[block.index] = block;
                        //console.log('Added block to free space ' + block.blockHash);
                        return true;
                    }
                    return false;
                });

                if (!putInFreeSpace) {
                    var newColumn = [];
                    newColumn[block.index] = block;
                    columns.push(newColumn);
                    //console.log('New column created to put block ' + block.blockHash);
                }
            }
        }
    }

    /**
     * @param columns - already added blocks placed in columns from root to leaf
     * @param newColumn - new array of blocks to add, starts from root block till leaf
     */
    function addColumn(columns, newColumn) {
        //console.log('Checking where to insert column ' + newColumn.map(function(b) {return b.blockHash}));

        if (columns.length == 0) {
            columns.push(newColumn);
            console.log('Added first column ' + columns.length);
        } else {
            var insertedInColumns = columns.some(function(column) {
                var isFreeColumn = newColumn.every(function(b) {
                    console.log('Checking content of ' + b.index + ' ' + column[b.index]);
                    return ((column.length < b.index) || (!column[b.index]));
                    //&& ((column.length - 1 < b.index) || (!column[b.index]));
                });

                if (isFreeColumn) {
                    console.log(column);
                    console.log('Added column to existing column ' + columns.length);
                    newColumn.forEach(function(b) {
                        column[b.index] = b;
                    });
                    console.log(column);
                }
                return isFreeColumn;
            });
            if (!insertedInColumns) {
                var colToAdd = [];
                newColumn.forEach(function(b) {
                    colToAdd[b.index] = b;
                });
                columns.push(colToAdd);
                console.log('Added new column as no space left ' + columns.length);
            }
        }
    }


    function addBlockViaSorting(data) {
        // find out hardest leaf
        var sortedBlocks = data
            .map(function(b) {return b;})
            .sort(function(a,b) {
                return a.totalDifficulty - b.totalDifficulty;
            })
            .reverse();
        var reversedData = data.map(function(b) {return b;}).reverse();

        var isCanonical = true;
        var renderColumns = [];
        while (sortedBlocks.length > 0) {
            console.log('Blocks left ' + sortedBlocks.map(blockHashFun));
            var leaf = sortedBlocks.shift();
            console.log('Canonical leaf block ' + leaf.blockHash);
            var col = reversedData.reduce(function(c, block) {
                if (c[0].parentHash == block.blockHash) {
                    c.unshift(block);
                }
                return c;
            }, [leaf]);

            col.forEach(function(b) {
                if (sortedBlocks.indexOf(b) > -1) {
                    sortedBlocks.splice(sortedBlocks.indexOf(b), 1);
                    reversedData.splice(reversedData.indexOf(b), 1);
                    console.log('Blocks left after remove ' + sortedBlocks.map(blockHashFun));
                }
            });

            if (isCanonical) {
                col.forEach(function(b) {
                    b.isCanonical = true;
                });
            }
            isCanonical = false;

            addColumn(renderColumns, col);
        }
        return renderColumns;
    }


    /**
     * @param renderColumns - current state
     */
    function renderState(svgContainer, renderColumns, blockNumbers, width, height) {

        //console.log('renderColumns ' + renderColumns.length);

        var columnCount = renderColumns.length;
        var blockWidth = Math.min(MAX_BLOCK_WIDTH, (width - NUMBERING_WIDTH - GAP * (columnCount + 1)) / columnCount);
        //console.log('Block width ' + blockWidth);

        var blocks = [];

        renderColumns
            .forEach(function(column, c) {
                column.forEach(function(block, n) {
                    //console.log('Adding block for rendering ' + block);
                    blocks.push({
                        text:   '0x' + block.blockHash.substr(0, 4),
                        x:      NUMBERING_WIDTH + GAP * (c + 1) + blockWidth * c,
                        y:      height - GAP * (n + 1) - BLOCK_HEIGHT * (n + 1),
                        width:  blockWidth,
                        height: BLOCK_HEIGHT,
                        isCanonical: block.isCanonical
                    });
                });
            });


        var blockNumberObjects = blockNumbers
            .map(function(blockNumber, i) {
                return {
                    x:      GAP,
                    y:      height - GAP * (i + 1) - BLOCK_HEIGHT * (i + 1),
                    width:  NUMBERING_WIDTH - 2 * GAP,
                    height: BLOCK_HEIGHT,
                    text:   blockNumber
                };
            });

        //console.log('blockNumberObjects');
        //console.log(blockNumberObjects);

        // Clear all
        svgContainer
            .attr('width', width)
            .attr('height', height)
            .selectAll('*')
            .remove();

        var numberingContainer = svgContainer
            .append('g')
            .attr('id', 'numberingContainer');

        var blockContainer = svgContainer
            .append('g')
            .attr('id', 'blockContainer');

        // Blocks
        blockContainer
            .selectAll('rect')
            .data(blocks)
            .enter()
            .append('rect')
            .attr('x', function (d) { return d.x; })
            .attr('y', function (d) { return d.y; })
            .attr('width', function (d) { return d.width; })
            .attr('height', function (d) { return d.height; })
            .style('fill', function (d) { return '#5E9CD3'; })
            .style("stroke", '#41719C')
            .style("stroke-width", 2);

        // Marking canonical
        var lineFunction = d3.svg
            .line()
            .x(function(d) { return d.x; })
            .y(function(d) { return d.y; })
            .interpolate("linear");
        blocks
            .forEach(function(b) {
                if (b.isCanonical) {
                    var lLineData = markerLeftLineData(b);
                    blockContainer
                        .append("path")
                        .attr("d", lineFunction(lLineData))
                        .attr("stroke", "#FFD966")
                        .attr("stroke-width", 3)
                        .attr("fill", "none");

                    var rLineData = markerRightLineData(b);
                    blockContainer
                        .append("path")
                        .attr("d", lineFunction(rLineData))
                        .attr("stroke", "#FFD966")
                        .attr("stroke-width", 3)
                        .attr("fill", "none");
                }
            });



        // Block labels
        blockContainer
            .selectAll('text')
            .data(blocks)
            .enter()
            .append('text')
            .attr('x', function(d) { return d.x + d.width / 2; })
            .attr('y', function(d) { return d.y + 20; })
            .text( function (d) { return d.text; })
            .attr('font-family', 'sans-serif')
            .attr('text-anchor', 'middle')
            .attr('font-size', '16px')
            .attr('fill', '##5E9CD3');

        // Left Block Numbers
        numberingContainer
            .selectAll('text')
            .data(blockNumberObjects)
            .enter()
            .append('text')
            .attr('x', function(d) { return d.x + NUMBERING_WIDTH / 2; })
            .attr('y', function(d) { return d.y + 20; })
            .text( function (d) { return d.text; })
            .attr('font-family', 'sans-serif')
            .attr('text-anchor', 'middle')
            .attr('font-size', '16px')
            .attr('fill', '#C5E0B4');

        // Blue vertical line
        numberingContainer
            .append("path")
            .attr("d", lineFunction([
                point(NUMBERING_WIDTH, 0),
                point(NUMBERING_WIDTH, height)
            ]))
            .attr("stroke", "#41719C")
            .attr("stroke-width", 2)
            .attr("fill", "none");

    }

    function ChartComponent(element, config) {

        config = config || {};
        var width = config.width || 400;
        var height = config.height || 400;

        var svgContainer = d3.select(element)
            .append('svg');

        var self = this;

        /**
         * @data - component creates copy
         */
        self.setData = function(data) {
            data = data || [];

            if (data.length == 0) {
                return;
            }

            // deep clone
            data = jQuery.extend(true, [], data);

            var blockNumbers = prepareData(data);

            var numbersCount = blockNumbers.length;
            var requiredHeight = Math.max(BLOCK_HEIGHT * numbersCount + GAP * (numbersCount + 1), height);

            var renderColumns = [];
            data.forEach(function(b) {
                addBlock(renderColumns, b);
            });

            var colCount = renderColumns.length;
            var requiredWidth = Math.max(width, NUMBERING_WIDTH + MIN_BLOCK_WIDTH * colCount + GAP * (colCount + 1));
            //var renderColumns = addBlockViaSorting(data);

            renderState(svgContainer, renderColumns, blockNumbers, requiredWidth, requiredHeight);
            return self;
        };

        // not implemented
        self.setWidth = function(newWidth) {
            if (width != newWidth) {
                width = newWidth;
            }
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