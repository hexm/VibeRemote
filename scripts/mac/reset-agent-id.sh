#!/bin/bash

echo "========================================"
echo "Reset Agent ID"
echo "========================================"
echo
echo "This script will delete the saved Agent ID."
echo "Next time the agent starts, it will register as a new agent."
echo
echo "Saved Agent ID file location:"
echo "$HOME/.lightscript/.agent_id"
echo

read -p "Press Enter to continue or Ctrl+C to cancel..."

if [ -f "$HOME/.lightscript/.agent_id" ]; then
    rm "$HOME/.lightscript/.agent_id"
    echo
    echo "✓ Agent ID file deleted successfully!"
    echo
    echo "Next time you start the agent, it will register as a new agent."
else
    echo
    echo "ℹ No saved Agent ID file found."
fi

echo