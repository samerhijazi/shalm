// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

/// @dev Simple token for the Shalm demo — not production-grade.
contract SimpleToken {
    string  public name     = "ShalmToken";
    string  public symbol   = "SHT";
    uint8   public decimals = 0;

    mapping(address => uint256) private _balances;
    uint256 private _totalSupply;

    event Transfer(address indexed from, address indexed to, uint256 amount);

    constructor() {
        // Seed the three demo validator accounts
        _mint(0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73, 1000);
        _mint(0x627306090abaB3A6e1400e9345bC60c78a8BEf57, 1000);
        _mint(0xf17f52151EbEF6C7334FAD080c5704D77216b732, 1000);
    }

    function totalSupply() external view returns (uint256) { return _totalSupply; }

    function balanceOf(address account) external view returns (uint256) {
        return _balances[account];
    }

    function transfer(address to, uint256 amount) external returns (bool) {
        require(_balances[msg.sender] >= amount, "Insufficient balance");
        _balances[msg.sender] -= amount;
        _balances[to]         += amount;
        emit Transfer(msg.sender, to, amount);
        return true;
    }

    function _mint(address to, uint256 amount) internal {
        _balances[to] += amount;
        _totalSupply  += amount;
        emit Transfer(address(0), to, amount);
    }
}
