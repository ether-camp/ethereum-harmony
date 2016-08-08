var BlockchainView = (function () {

    var GAP = 8;
    var BLOCK_HEIGHT = 30;
    var NUMBERING_WIDTH = 60;
    var BLANK_HASH = -1;
    var MIN_COLUMNS = 3;
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

    /**
     * @param state
     * @param block
     */
    function addBlock(state, block) {

    }

    /**
     * @param columns - already added blocks placed in columns from root to leaf
     * @param newColumn - new array of blocks to add, starts from root block till leaf
     */
    function addColumn(columns, newColumn) {
        console.log('Checking where to insert column ' + newColumn.map(function(b) {return b.blockHash}));

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
        var blockHashFun = function(b) {return b.blockHash};

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

        console.log('renderColumns ' + renderColumns.length);
        console.log(renderColumns);

        var columnCount = renderColumns.length;
        var blockWidth = Math.min(MAX_BLOCK_WIDTH, (width - NUMBERING_WIDTH - GAP * (columnCount + 1)) / columnCount);
        console.log('Block width ' + blockWidth);

        var blocks = [];

        renderColumns
            .forEach(function(column, c) {
                column.forEach(function(block, n) {
                    if (block != BLANK_HASH) {
                        //console.log('Adding block for rendering ' + block);
                        blocks.push({
                            text:   block.blockHash,
                            x:      NUMBERING_WIDTH + GAP * (c + 1) + blockWidth * c,
                            y:      height - GAP * (n + 1) - BLOCK_HEIGHT * (n + 1),
                            width:  blockWidth,
                            height: BLOCK_HEIGHT,
                            isCanonical: block.isCanonical
                        });
                    }
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

        console.log('blockNumberObjects');
        console.log(blockNumberObjects);

        var numberingContainer = svgContainer
            .append("g")
            .attr("id", 'numberingContainer');

        var blockContainer = svgContainer
            .append("g")
            .attr("id", 'blockContainer');

        blockContainer
            .selectAll("rect")
            .data(blocks)
            .enter()
            .append("rect")
            .attr("x", function (d) { return d.x; })
            .attr("y", function (d) { return d.y; })
            .attr("width", function (d) { return d.width; })
            .attr("height", function (d) { return d.height; })
            .style("fill", function (d) { return "#5E9CD3"; });


        blockContainer
            .selectAll("text")
            .data(blocks)
            .enter()
            .append("text")
            .attr("x", function(d) { return d.x + 15; })
            .attr("y", function(d) { return d.y + 22; })
            .text( function (d) { return d.text; })
            .attr("font-family", "sans-serif")
            .attr("text-anchor", "middle")
            .attr("font-size", "20px")
            .attr("fill", "##5E9CD3");

        // Left Numbers
        numberingContainer
            .selectAll("text")
            .data(blockNumberObjects)
            .enter()
            .append("text")
            .attr("x", function(d) { return d.x + 15; })
            .attr("y", function(d) { return d.y + 22; })
            .text( function (d) { return d.text; })
            .attr("font-family", "sans-serif")
            .attr("text-anchor", "middle")
            .attr("font-size", "20px")
            .attr("fill", "#C5E0B4");

        // Marking canonical
    }

    var Chart = function(element, config) {

        config = config || {};
        var width = config.width || 600;
        var height = config.height || 600;
        var state = []; // array of arrays of blocks
        var blockStore = {};
        var blockNumbers = [];

        var svgContainer = d3.select(element)
            .append("svg")
            .attr("width", width)
            .attr("height", height);


        var self =  {
            setData: function(data) {
                data = data || [];

                if (data.length == 0) {
                    return;
                }

                var parentsMap = {};
                data.forEach(function(b) {
                    parentsMap[b.blockHash] = true;
                    blockStore[b.blockHash] = b;
                    if (blockNumbers.indexOf(b.blockNumber) < 0) {
                        blockNumbers.push(b.blockNumber);
                    }
                });
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


                var renderColumns = addBlockViaSorting(data);


                renderState(svgContainer, renderColumns, blockNumbers, width, height);
                return self;
            },

            addBlock: function(block) {
                addBlock(state, block);
            }
        };
        return self;
    };

    return {
        create: function(element, config) {
            return new Chart(element, config);
        }
    }
})();