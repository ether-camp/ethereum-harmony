pragma solidity ^0.4.0;

contract owned {
  address owner;
  function owned() {
    owner = msg.sender;
  }
  function changeOwner(address newOwner) onlyowner {
    owner = newOwner;
  }
  modifier onlyowner() {
    if (msg.sender==owner) _;
  }
}

contract mortal is owned {
  function kill() onlyowner {
    if (msg.sender == owner) suicide(owner);
  }
}

contract NameReg {
  function register(bytes32 name) {}
  function unregister() {}
  function addressOf(bytes32 name) constant returns (address addr) {}
  function nameOf(address addr) constant returns (bytes32 name) {}
  function kill() {}
}

contract nameRegAware {
  function nameRegAddress() returns (address) {
    return 0x14acb61d97243015f36bf1db353a47672f17cffe;
  }

  function named(bytes32 name) returns (address) {
    return NameReg(nameRegAddress()).addressOf(name);
  }
}

contract named is nameRegAware {
  function named(bytes32 name) {
    NameReg(nameRegAddress()).register(name);
  }
}

// contract with util functions
contract util {
  // Converts 'string' to 'bytes32'
  function s2b(string s) internal returns (bytes32) {
      bytes memory b = bytes(s);
      uint r = 0;
      for (uint i = 0; i < 32; i++) {
          if (i < b.length) {
              r = r | uint(b[i]);
          }
          if (i < 31) r = r * 256;
      }
      return bytes32(r);
  }
}
