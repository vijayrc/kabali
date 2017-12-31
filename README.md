# kabali

<img src='https://raw.githubusercontent.com/vijayaraj210/kabali/master/kabali-logo.png'/>

## Problem
- *Given* big data jobs have widely varying business needs
- *and* operate in a shifting environment with respect to data, software and operations,
- *when* I must assert their sanity regressively,
- *then* I need a hassle-free, transparent mechanism to enforce sampling of the job runs in any environment

## Solution 
*Kabali* is a library which
- formalizes proportionate stratified sampling approach
- enables fail-fast in production by first running the sample before entire population
- easy declarative configuration for analysts
- enforces sampling need contracts on developers, yet transparent, minimal and non-intrusive.
